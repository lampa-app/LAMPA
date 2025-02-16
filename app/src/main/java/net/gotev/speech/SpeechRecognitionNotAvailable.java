package net.gotev.speech;

/**
 * @author Aleksandar Gotev
 */
public class SpeechRecognitionNotAvailable extends Exception {
    public SpeechRecognitionNotAvailable() {
        super("Speech recognition not available");
    }
}
