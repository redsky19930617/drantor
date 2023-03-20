package org.owntracks.android.services

import android.content.Context
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.idling.CountingIdlingResource
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.BlockingDeque
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.owntracks.android.data.EndpointState
import org.owntracks.android.data.repos.ContactsRepo
import org.owntracks.android.data.repos.EndpointStateRepo
import org.owntracks.android.data.repos.WaypointsRepo
import org.owntracks.android.di.ApplicationScope
import org.owntracks.android.di.CoroutineScopes.IoDispatcher
import org.owntracks.android.model.CommandAction
import org.owntracks.android.model.messages.*
import org.owntracks.android.preferences.Preferences
import org.owntracks.android.preferences.types.ConnectionMode
import org.owntracks.android.services.worker.Scheduler
import org.owntracks.android.support.Parser
import org.owntracks.android.support.ServiceBridge
import org.owntracks.android.support.SimpleIdlingResource
import org.owntracks.android.support.interfaces.ConfigurationIncompleteException
import org.owntracks.android.support.interfaces.StatefulServiceMessageProcessor
import timber.log.Timber

@Singleton
class MessageProcessor @Inject constructor(
    @ApplicationContext applicationContext: Context,
    private val contactsRepo: ContactsRepo,
    private val preferences: Preferences,
    private val waypointsRepo: WaypointsRepo,
    parser: Parser,
    scheduler: Scheduler,
    private val endpointStateRepo: EndpointStateRepo,
    private val serviceBridge: ServiceBridge,
    private val outgoingQueueIdlingResource: CountingIdlingResource,
    private val locationProcessorLazy: Lazy<LocationProcessor>,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope
) : Preferences.OnPreferenceChangeListener {
    private var endpoint: MessageProcessorEndpoint? = null
    private var acceptMessages = false
    private val outgoingQueue: BlockingDeque<MessageBase>
    private var backgroundDequeueThread: Thread? = null
    private var initialized = false
    private var waitFuture: ScheduledFuture<*>? = null
    private var retryWait = SEND_FAILURE_BACKOFF_INITIAL_WAIT
    private val httpEndpoint: MessageProcessorEndpoint
    private val mqttEndpoint: MessageProcessorEndpoint
    private val locker = LockerWithCondition()

    data class LockerWithCondition(
        val lock: ReentrantLock = ReentrantLock(),
        val condition: Condition = lock.newCondition()
    )

    init {
        outgoingQueue = BlockingDequeThatAlsoSometimesPersistsThingsToDiskMaybe(
            10000,
            applicationContext.filesDir,
            parser
        )
        synchronized(outgoingQueue) {
            for (i in outgoingQueue.indices) {
                outgoingQueueIdlingResource.increment()
            }
            Timber.d("Initializing the outgoingQueueIdlingResource at ${outgoingQueue.size})")
        }
        preferences.registerOnPreferenceChangedListener(this)
        httpEndpoint = HttpMessageProcessorEndpoint(
            this,
            parser,
            preferences,
            applicationContext,
            endpointStateRepo,
            scope,
            ioDispatcher
        )
        mqttEndpoint = MQTTMessageProcessorEndpoint(
            this,
            endpointStateRepo,
            scheduler,
            preferences,
            parser,
            scope,
            ioDispatcher,
            applicationContext
        )
    }

    @Synchronized
    fun initialize() {
        if (!initialized) {
            Timber.d("Initializing MessageProcessor")
            endpointStateRepo.setState(EndpointState.INITIAL)
            scope.launch {
                reconnect()
                initialized = true
            }
        }
    }

    /**
     * Called either by the connection activity user button, or by receiving a RECONNECT message
     */
    suspend fun reconnect(): Boolean? {
        return when (endpoint) {
            null -> {
                loadOutgoingMessageProcessor() // The processor should take care of the reconnect on init
                null
            }
            is MQTTMessageProcessorEndpoint -> {
                (endpoint as MQTTMessageProcessorEndpoint).reconnect()
            }
            else -> {
                null
            }
        }
    }

    fun statefulCheckConnection(): Boolean {
        return if (endpoint is StatefulServiceMessageProcessor) {
            (endpoint as StatefulServiceMessageProcessor).checkConnection()
        } else {
            true
        }
    }

    val isEndpointReady: Boolean
        get() {
            try {
                if (endpoint != null) {
                    endpoint!!.getEndpointConfiguration()
                    return true
                }
            } catch (e: ConfigurationIncompleteException) {
                return false
            }
            return false
        }

    private fun loadOutgoingMessageProcessor() {
        Timber.d("Reloading outgoing message processor")
        endpoint?.deactivate()
            .also { Timber.d("Destroying previous endpoint") }
        endpointStateRepo.setQueueLength(outgoingQueue.size)
        endpoint = when (preferences.mode) {
            ConnectionMode.HTTP -> httpEndpoint
            ConnectionMode.MQTT -> mqttEndpoint
        }
        if (backgroundDequeueThread == null || !backgroundDequeueThread!!.isAlive) {
            // Create the background thread that will handle outbound messages
            backgroundDequeueThread = Thread({ sendAvailableMessages() }, "backgroundDequeueThread").apply { start() }
        }
        endpoint!!.activate()
        acceptMessages = true
    }

    fun queueMessageForSending(message: MessageBase) {
        if (!acceptMessages) return
        outgoingQueueIdlingResource.increment()
        Timber.d(
            "Queueing messageId:${message.messageId}, current queueLength:${outgoingQueue.size}"
        )
        synchronized(outgoingQueue) {
            if (!outgoingQueue.offer(message)) {
                val droppedMessage = outgoingQueue.poll()
                Timber.e("Outgoing queue full. Dropping oldest message: $droppedMessage")
                if (!outgoingQueue.offer(message)) {
                    Timber.e("Still can't put message onto the queue. Dropping: $message")
                }
            }
            endpointStateRepo.setQueueLength(outgoingQueue.size)
        }
    }

    // Should be on the background thread here, because we block
    private fun sendAvailableMessages() {
        Timber.d("Starting outbound message loop.")
        var previousMessageFailed = false
        var retriesToGo = 0
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        while (true) {
            try {
                val message: MessageBase = outgoingQueue.take() // <--- blocks
                Timber.v("Taken message off queue: $message")
                endpointStateRepo.setQueueLength(outgoingQueue.size + 1)
                if (!previousMessageFailed) {
                    retriesToGo = message.numberOfRetries
                }
                try {
                    previousMessageFailed = false
                    retryWait = SEND_FAILURE_BACKOFF_INITIAL_WAIT
                    endpoint!!.sendMessage(message)
                } catch (e: Exception) {
                    when (e) {
                        is OutgoingMessageSendingException,
                        is ConfigurationIncompleteException -> {
                            Timber.w("Error sending message. Re-queueing")
                            synchronized(outgoingQueue) {
                                if (!outgoingQueue.offerFirst(message)) {
                                    val tailMessage = outgoingQueue.removeLast()
                                    Timber.w(
                                        "Queue full when trying to re-queue failed message. " +
                                            "Dropping last message: $tailMessage"
                                    )
                                    if (!outgoingQueue.offerFirst(message)) {
                                        Timber.e(
                                            "Couldn't restore failed message back onto the queue, dropping: $message"
                                        )
                                    }
                                }
                            }
                            previousMessageFailed = true
                            retriesToGo -= 1
                        }
                        else -> {
                            Timber.e(e, "Couldn't send message. Dropping and moving on")
                            retryWait = SEND_FAILURE_BACKOFF_INITIAL_WAIT
                            previousMessageFailed = false
                        }
                    }
                }

                if (previousMessageFailed && retriesToGo <= 0) {
                    previousMessageFailed = false
                } else if (previousMessageFailed) {
                    Timber.i("Waiting for $retryWait before retrying")
                    waitFuture = scheduler.schedule(
                        {
                            locker.lock.withLock { locker.condition.await() }
                        },
                        retryWait.inWholeMilliseconds,
                        TimeUnit.MILLISECONDS
                    )
                    locker.lock.withLock { locker.condition.await() }
                    retryWait = (retryWait).coerceAtMost(SEND_FAILURE_BACKOFF_MAX_WAIT)
                } else {
                    try {
                        if (!outgoingQueueIdlingResource.isIdleNow) {
                            Timber.v("Decrementing outgoingQueueIdlingResource")
                            outgoingQueueIdlingResource.decrement()
                        }
                    } catch (e: IllegalStateException) {
                        Timber.w(e, "outgoingQueueIdlingResource is invalid")
                    }
                }
            } catch (e: InterruptedException) {
                Timber.i(e, "Outgoing message loop interrupted")
                break
            }
        }
        scheduler.shutdown()
        Timber.w("Exiting outgoing message loop")
    }

    /**
     * Resets the retry backoff timer back to the initial value, because we've most likely had a
     * reconnection event.
     */
    fun notifyOutgoingMessageQueue() {
        waitFuture?.run {
            cancel(false)
            Timber.d("Resetting message send loop wait.")
            retryWait = SEND_FAILURE_BACKOFF_INITIAL_WAIT
            locker.lock.withLock { locker.condition.signal() }
        }
    }

    fun onMessageDelivered() {
        endpointStateRepo.setQueueLength(outgoingQueue.size)
    }

    fun onMessageDeliveryFailedFinal(messageId: String?) {
        Timber.e("Message delivery failed, not retryable. $messageId")
        endpointStateRepo.setQueueLength(outgoingQueue.size)
    }

    fun onMessageDeliveryFailed(messageId: String?) {
        Timber.e("Message delivery failed. queueLength: ${outgoingQueue.size + 1}, messageId: $messageId")
        endpointStateRepo.setQueueLength(outgoingQueue.size)
    }

    fun processIncomingMessage(message: MessageBase) {
        Timber.i(
            "Received incoming message: %s on %s",
            message.javaClass.simpleName,
            message.contactKey
        )
        when (message) {
            is MessageClear -> {
                processIncomingMessage(message)
            }
            is MessageLocation -> {
                processIncomingMessage(message)
            }
            is MessageCard -> {
                processIncomingMessage(message)
            }
            is MessageCmd -> {
                processIncomingMessage(message)
            }
            is MessageTransition -> {
                processIncomingMessage(message)
            }
        }
    }

    private fun processIncomingMessage(message: MessageClear) {
        contactsRepo.remove(message.contactKey)
    }

    private fun processIncomingMessage(message: MessageLocation) {
        // do not use TimeUnit.DAYS.toMillis to avoid long/double conversion issues...
        if (preferences.ignoreStaleLocations > 0 &&
            System.currentTimeMillis() - message.timestamp * 1000 >
            preferences.ignoreStaleLocations.toDouble().days.inWholeMilliseconds
        ) {
            Timber.e("discarding stale location")
        } else {
            contactsRepo.update(message.contactKey, message)
        }
    }

    private fun processIncomingMessage(message: MessageCard) {
        contactsRepo.update(message.contactKey, message)
    }

    private fun processIncomingMessage(message: MessageCmd) {
        if (!preferences.cmd) {
            Timber.w("remote commands are disabled")
            return
        }
        if (message.modeId !== ConnectionMode.HTTP &&
            preferences.receivedCommandsTopic != message.topic
        ) {
            Timber.e("cmd message received on wrong topic")
            return
        }
        if (!message.isValidMessage()) {
            Timber.e("Invalid action message received")
            return
        }
        if (message.action != null) {
            when (message.action) {
                CommandAction.REPORT_LOCATION -> {
                    if (message.modeId !== ConnectionMode.MQTT) {
                        Timber.e("command not supported in HTTP mode: ${message.action})")
                    } else {
                        serviceBridge.requestOnDemandLocationFix()
                    }
                }
                CommandAction.WAYPOINTS -> locationProcessorLazy.get()
                    .publishWaypointsMessage()
                CommandAction.SET_WAYPOINTS -> if (message.waypoints != null) {
                    waypointsRepo.importFromMessage(message.waypoints!!.waypoints)
                }
                CommandAction.SET_CONFIGURATION -> {
                    if (!preferences.remoteConfiguration) {
                        Timber.w("Received a remote configuration command but remote config setting is disabled")
                    }
                    if (message.configuration != null) {
                        preferences.importConfiguration(message.configuration!!)
                    } else {
                        Timber.w("No configuration provided")
                    }
                    if (message.waypoints != null) {
                        waypointsRepo.importFromMessage(message.waypoints!!.waypoints)
                    }
                }
                CommandAction.RECONNECT -> {
                    if (message.modeId !== ConnectionMode.HTTP) {
                        Timber.e("command not supported in HTTP mode: ${message.action}")
                    } else {
                        scope.launch {
                            reconnect()
                        }
                    }
                }
                else -> {}
            }
        }
    }

    fun publishLocationMessage(trigger: String?) {
        locationProcessorLazy.get()
            .publishLocationMessage(trigger)
    }

    private fun processIncomingMessage(message: MessageTransition) {
        serviceBridge.sendEventNotification(message)
    }

    fun stopSendingMessages() {
        Timber.d("Interrupting background sending thread")
        backgroundDequeueThread!!.interrupt()
    }

    override fun onPreferenceChanged(properties: Set<String>) {
        if (properties.contains("mode")) {
            acceptMessages = false
            loadOutgoingMessageProcessor()
        }
    }

    val mqttConnectionIdlingResource: IdlingResource
        get() = if (endpoint is MQTTMessageProcessorEndpoint) {
            (endpoint as MQTTMessageProcessorEndpoint?)!!.mqttConnectionIdlingResource
        } else {
            SimpleIdlingResource("alwaysIdle", true)
        }

    companion object {
        private val SEND_FAILURE_BACKOFF_INITIAL_WAIT = 1.seconds
        private val SEND_FAILURE_BACKOFF_MAX_WAIT = 2.minutes
    }
}
