package net.gotev.speech;

/**
 * @author Aleksandar Gotev
 */

public class GoogleVoiceTypingDisabledException extends Exception {
    public GoogleVoiceTypingDisabledException() {
        super("Google voice typing must be enabled");
    }
}
