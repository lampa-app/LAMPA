package net.gotev.speech;

import android.speech.SpeechRecognizer;

/**
 * Speech recognition exception.
 *
 * @author Aleksandar Gotev
 */
public class SpeechRecognitionException extends Exception {

    private int code;

    public SpeechRecognitionException(int code) {
        super(getMessage(code));
        this.code = code;
    }

    private static String getMessage(int code) {
        String message;

        // these have been mapped from here:
        // https://developer.android.com/reference/android/speech/SpeechRecognizer.html
        switch(code) {
            case SpeechRecognizer.ERROR_AUDIO:
                message = code + " - Audio recording error";
                break;

            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                message = code + " - Insufficient permissions. Request android.permission.RECORD_AUDIO";
                break;

            case SpeechRecognizer.ERROR_CLIENT:
                // http://stackoverflow.com/questions/24995565/android-speechrecognizer-when-do-i-get-error-client-when-starting-the-voice-reco
                message = code + " - Client side error. Maybe your internet connection is poor!";
                break;

            case SpeechRecognizer.ERROR_NETWORK:
                message = code + " - Network error";
                break;

            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                message = code + " - Network operation timed out";
                break;

            case SpeechRecognizer.ERROR_NO_MATCH:
                message = code + " - No recognition result matched. Try turning on partial results as a workaround.";
                break;

            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                message = code + " - RecognitionService busy";
                break;

            case SpeechRecognizer.ERROR_SERVER:
                message = code + " - Server sends error status";
                break;

            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                message = code + " - No speech input";
                break;

            default:
                message = code + " - Unknown exception";
                break;
        }

        return message;
    }

    public int getCode() {
        return code;
    }
}
