package app.visiblesound.source

import app.visiblesound.core.AppEvent
import app.visiblesound.core.Seat

/**
 * The public speech SDK does not say who spoke, so the speaker comes from who is in the car.
 * With more than one passenger no seat is claimed rather than guessing wrong.
 */
object SpeakerLocator {
    fun attribute(text: String, isFinal: Boolean, occupied: Set<Seat>, outsideMode: Boolean, at: Long): AppEvent {
        if (outsideMode) return AppEvent.Outside(text, isFinal, at)
        val passengers = occupied - Seat.DRIVER
        val seat = when (passengers.size) {
            0 -> Seat.DRIVER
            1 -> passengers.single()
            else -> null
        }
        return AppEvent.Caption(seat, text, isFinal, at)
    }
}

/** Sounds are always classified; this decides whether speech recognition hears the stream too. */
object MicArbiter {
    fun speechOpen(hasPassenger: Boolean, outsideMode: Boolean, ttsPlaying: Boolean) =
        (hasPassenger || outsideMode) && !ttsPlaying
}
