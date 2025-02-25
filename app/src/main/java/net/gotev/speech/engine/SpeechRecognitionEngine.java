package net.gotev.speech.engine;

import android.content.Context;
import android.speech.RecognitionListener;

import net.gotev.speech.SpeechDelegate;
import net.gotev.speech.GoogleVoiceTypingDisabledException;
import net.gotev.speech.SpeechRecognitionNotAvailable;
import net.gotev.speech.ui.SpeechProgressView;

import java.util.Locale;

public interface SpeechRecognitionEngine extends RecognitionListener {

    void init(Context context);

    void clear();

    String getPartialResultsAsString();

    void initSpeechRecognizer(Context context);

    void startListening(SpeechProgressView progressView, SpeechDelegate delegate) throws SpeechRecognitionNotAvailable, GoogleVoiceTypingDisabledException;

    void stopListening();

    void returnPartialResultsAndRecreateSpeechRecognizer();

    void setPartialResults(boolean getPartialResults);

    void shutdown();

    boolean isListening();

    Locale getLocale();

    void setLocale(Locale locale);

    void setPreferOffline(boolean preferOffline);

    void setTransitionMinimumDelay(long milliseconds);

    void setStopListeningAfterInactivity(long milliseconds);

    void setCallingPackage(String callingPackage);

    void unregisterDelegate();
}
