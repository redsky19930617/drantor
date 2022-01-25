package org.owntracks.android.testutils

import android.content.Intent
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.adevinta.android.barista.assertion.BaristaVisibilityAssertions.assertContains
import com.adevinta.android.barista.interaction.BaristaDrawerInteractions.openDrawer
import com.adevinta.android.barista.interaction.BaristaSleepInteractions.sleep
import kotlinx.coroutines.DelicateCoroutinesApi
import mqtt.broker.Broker
import mqtt.broker.interfaces.Authentication
import mqtt.broker.interfaces.PacketInterceptor
import mqtt.packets.MQTTPacket
import mqtt.packets.Qos
import mqtt.packets.mqttv5.MQTT5Properties
import org.eclipse.paho.client.mqttv3.internal.websocket.Base64
import org.owntracks.android.R
import org.owntracks.android.model.messages.MessageBase
import org.owntracks.android.support.Parser
import org.owntracks.android.ui.clickOnAndWait
import timber.log.Timber
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread


@ExperimentalUnsignedTypes
class TestWithMQTTBrokerImpl : TestWithAnMQTTBroker {
    private val mqttPort: Int = 18883
    override val mqttUsername = "testUser"
    override val mqttClientId = "testClientId"
    override val deviceId = "aa"
    private val mqttTestPassword = "testPassword"
    override val mqttPacketsReceived: MutableList<MQTTPacket> = mutableListOf()
    override lateinit var broker: Broker


    override fun <E : MessageBase> Collection<E>.sendFromBroker(broker: Broker) {
        map(Parser(null)::toJsonBytes)
            .forEach {
                broker.publish(
                    false,
                    "owntracks/someuser/somedevice",
                    Qos.AT_LEAST_ONCE,
                    MQTT5Properties(),
                    it.toUByteArray()
                )
            }
    }

    private lateinit var brokerThread: Thread
    private var shouldBeRunning = false

    @DelicateCoroutinesApi
    override fun startBroker() {
        mqttPacketsReceived.clear()
        Timber.i("Starting MQTT Broker")
        broker = createNewBroker()
        shouldBeRunning = true
        brokerThread = thread {
            while (shouldBeRunning) {
                Timber.i("Calling MQTT Broker listen")
                broker.listen()
                Timber.i("MQTT Broker no longer listening")
            }
            Timber.i("MQTT Broker Thread ending")
        }
        var listening = false
        while (!listening) {
            Socket().use {
                try {
                    it.apply { connect(InetSocketAddress("localhost", mqttPort)) }
                    listening = true

                } catch (e: ConnectException) {
                    Timber.i(e, "broker not listening on $mqttPort yet")
                    Thread.sleep(100)
                }
            }
        }
        Timber.i("Test MQTT Broker listening")
    }

    private fun createNewBroker(): Broker =
        Broker(host = "127.0.0.1",
            port = mqttPort,
            authentication = object : Authentication {
                override fun authenticate(
                    clientId: String,
                    username: String?,
                    password: UByteArray?
                ): Boolean {
                    return username == mqttUsername && password.contentEquals(
                        mqttTestPassword.toByteArray().toUByteArray()
                    )
                }
            },
            packetInterceptor = object : PacketInterceptor {
                override fun packetReceived(
                    clientId: String,
                    username: String?,
                    password: UByteArray?,
                    packet: MQTTPacket
                ) {
                    Timber.d("MQTT Packet received $packet")
                    mqttPacketsReceived.add(packet)
                }
            })


    override fun stopBroker() {
        shouldBeRunning = false
        Timber.i("Requesting MQTT Broker stop")
        broker.stop()
        Timber.i("Waiting to join thread")
        brokerThread.join()
        Timber.i("MQTT Broker stopped")
    }

    override fun configureMQTTConnectionToLocal(password: String) {
        val config = Base64.encode(
            """
            {
                "_type": "configuration",
                "clientId": "$mqttClientId",
                "deviceId": "$deviceId",
                "host": "127.0.0.1",
                "password": "$password",
                "port": $mqttPort,
                "username": "$mqttUsername",
                "tls": false,
                "mqttConnectionTimeout": 1
            }
        """.trimIndent()
        )
        InstrumentationRegistry.getInstrumentation().targetContext.startActivity(Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("owntracks:///config?inline=$config")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
        sleep(500)
        clickOnAndWait(R.id.save)
        openDrawer()
        clickOnAndWait(R.string.title_activity_status)
    }

    // This will use the right password, so we should test for success
    override fun configureMQTTConnectionToLocal() {
        configureMQTTConnectionToLocal(mqttTestPassword)
        sleep(2000)
        assertContains(R.id.connectedStatus, R.string.CONNECTED)
    }
}