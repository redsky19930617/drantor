package org.owntracks.android.support

import androidx.test.espresso.IdlingResource
import timber.log.Timber

/**
 * Idling resource that tracks data
 *
 * @param T
 * @constructor Create empty Idling resource with data
 * @property resourceName
 */
class IdlingResourceWithData<T>(
    private val resourceName: String,
    private val comparator: Comparator<in T>
) : IdlingResource {
  private var callback: IdlingResource.ResourceCallback? = null
  private val sent = mutableListOf<T>()
  private val received = mutableListOf<T>()

  override fun getName(): String = this.resourceName

  override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback?) {
    this.callback = callback
  }

  override fun isIdleNow(): Boolean = sent.isEmpty() && received.isEmpty()

  fun add(thing: T) {
    synchronized(sent) {
      synchronized(received) {
        Timber.v("Waiting for return for $thing")
        sent.add(thing)
        reconcile()
      }
    }
  }

  fun remove(thing: T) {
    synchronized(sent) {
      synchronized(received) {
        Timber.v("Received return for $thing")
        received.add(thing)
        reconcile()
      }
    }
  }

  private fun reconcile() {
    Timber.v("Contents: sent=${sent.joinToString(",")}, received=${received.joinToString(",")}")
    sent.intersectByComparator(received.toSet(), comparator).let { sentToRemove ->
      received.intersectByComparator((sent.toSet()), comparator).let { receivedToRemove ->
        sent.removeAll(sentToRemove).also {
          Timber.v("Removed $sentToRemove from sent. Success = $it")
        }
        received.removeAll(receivedToRemove).also {
          Timber.v("Removed $receivedToRemove from received. Success = $it")
        }
      }
    }
    if (sent.isEmpty() && received.isEmpty()) {
      Timber.v("$name Empty. Idling.")
      callback?.onTransitionToIdle()
    }
  }

  private fun Collection<T>.intersectByComparator(
      another: Collection<T>,
      comparator: Comparator<in T>
  ): Set<T> {
    return this.filter { a -> another.any { b -> comparator.compare(a, b) == 0 } }.toSet()
  }
}
