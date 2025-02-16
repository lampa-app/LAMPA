package net.gotev.speech;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import net.gotev.speech.engine.BaseSpeechRecognitionEngine;
import net.gotev.speech.engine.DummyOnInitListener;
import net.gotev.speech.engine.SpeechRecognitionEngine;
import net.gotev.speech.engine.BaseTextToSpeechEngine;
import net.gotev.speech.engine.TextToSpeechEngine;
import net.gotev.speech.ui.SpeechProgressView;

import java.util.*;

/**
 * Helper class to easily work with Android speech recognition.
 *
 * @author Aleksandar Gotev
 */
public class Speech {

    private static Speech instance = null;
    protected static String GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox";

    private Context mContext;

    private TextToSpeechEngine textToSpeechEngine;
    private SpeechRecognitionEngine speechRecognitionEngine;

    private Speech(final Context context, final String callingPackage, TextToSpeech.OnInitListener onInitListener, SpeechRecognitionEngine speechRecognitionEngine, TextToSpeechEngine textToSpeechEngine) {
        mContext = context;

        this.speechRecognitionEngine = speechRecognitionEngine;
        this.speechRecognitionEngine.setCallingPackage(callingPackage);
        this.speechRecognitionEngine.initSpeechRecognizer(context);

        this.textToSpeechEngine = textToSpeechEngine;
        this.textToSpeechEngine.setOnInitListener(onInitListener);
        this.textToSpeechEngine.initTextToSpeech(context);
    }

    /**
     * Initializes speech recognition.
     *
     * @param context application context
     * @return speech instance
     */
    public static Speech init(final Context context) {
        if (instance == null) {
            instance = new Speech(context, null, new DummyOnInitListener(), new BaseSpeechRecognitionEngine(), new BaseTextToSpeechEngine());
        }

        return instance;
    }

    /**
     * Initializes speech recognition.
     *
     * @param context        application context
     * @param callingPackage The extra key used in an intent to the speech recognizer for
     *                       voice search. Not generally to be used by developers.
     *                       The system search dialog uses this, for example, to set a calling
     *                       package for identification by a voice search API.
     *                       If this extra is set by anyone but the system process,
     *                       it should be overridden by the voice search implementation.
     *                       By passing null or empty string (which is the default) you are
     *                       not overriding the calling package
     * @return speech instance
     */
    public static Speech init(final Context context, final String callingPackage) {
        if (instance == null) {
            instance = new Speech(context, callingPackage, new DummyOnInitListener(), new BaseSpeechRecognitionEngine(), new BaseTextToSpeechEngine());
        }

        return instance;
    }

    public static Speech init(final Context context, final String callingPackage, TextToSpeech.OnInitListener onInitListener) {
        if (instance == null) {
            instance = new Speech(context, callingPackage, onInitListener, new BaseSpeechRecognitionEngine(), new BaseTextToSpeechEngine());
        }

        return instance;
    }

    public static Speech init(final Context context, final String callingPackage, TextToSpeech.OnInitListener onInitListener, SpeechRecognitionEngine speechRecognitionEngine, TextToSpeechEngine textToSpeechEngine) {
        if (instance == null) {
            instance = new Speech(context, callingPackage, onInitListener, speechRecognitionEngine, textToSpeechEngine);
        }

        return instance;
    }

    /**
     * Must be called inside Activity's onDestroy.
     */
    public synchronized void shutdown() {
        speechRecognitionEngine.shutdown();
        textToSpeechEngine.shutdown();

        instance = null;
    }

    /**
     * Gets speech recognition instance.
     *
     * @return SpeechRecognition instance
     */
    public static Speech getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Speech recognition has not been initialized! call init method first!");
        }

        return instance;
    }

    /**
     * Starts voice recognition.
     *
     * @param delegate delegate which will receive speech recognition events and status
     * @throws SpeechRecognitionNotAvailable      when speech recognition is not available on the device
     * @throws GoogleVoiceTypingDisabledException when google voice typing is disabled on the device
     */
    public void startListening(final SpeechDelegate delegate)
            throws SpeechRecognitionNotAvailable, GoogleVoiceTypingDisabledException {
        startListening(null, delegate);
    }

    /**
     * Starts voice recognition.
     *
     * @param progressView view in which to draw speech animation
     * @param delegate     delegate which will receive speech recognition events and status
     * @throws SpeechRecognitionNotAvailable      when speech recognition is not available on the device
     * @throws GoogleVoiceTypingDisabledException when google voice typing is disabled on the device
     */
    public void startListening(final SpeechProgressView progressView, final SpeechDelegate delegate)
            throws SpeechRecognitionNotAvailable, GoogleVoiceTypingDisabledException {

        speechRecognitionEngine.startListening(progressView, delegate);
    }

    /**
     * Stops voice recognition listening.
     * This method does nothing if voice listening is not active
     */
    public void stopListening() {
        speechRecognitionEngine.stopListening();
    }

    /**
     * Check if voice recognition is currently active.
     *
     * @return true if the voice recognition is on, false otherwise
     */
    public boolean isListening() {
        return speechRecognitionEngine.isListening();
    }

    /**
     * Check if text to speak is currently speaking.
     *
     * @return true if the text to speak is speaking, false otherwise
     */
    public boolean isSpeaking() {
        return textToSpeechEngine.isSpeaking();
    }

    /**
     * Uses text to speech to transform a written message into a sound.
     *
     * @param message message to play
     */
    public void say(final String message) {
        say(message, null);
    }

    /**
     * Uses text to speech to transform a written message into a sound.
     *
     * @param message  message to play
     * @param callback callback which will receive progress status of the operation
     */
    public void say(final String message, final TextToSpeechCallback callback) {
        textToSpeechEngine.say(message, callback);
    }

    /**
     * Stops text to speech.
     */
    public void stopTextToSpeech() {
        textToSpeechEngine.stop();
    }

    /**
     * Set whether to only use an offline speech recognition engine.
     * The default is false, meaning that either network or offline recognition engines may be used.
     *
     * @param preferOffline true to prefer offline engine, false to use either one of the two
     * @return speech instance
     */
    public Speech setPreferOffline(final boolean preferOffline) {
        speechRecognitionEngine.setPreferOffline(preferOffline);
        return this;
    }

    /**
     * Set whether partial results should be returned by the recognizer as the user speaks
     * (default is true). The server may ignore a request for partial results in some or all cases.
     *
     * @param getPartialResults true to get also partial recognition results, false otherwise
     * @return speech instance
     */
    public Speech setGetPartialResults(final boolean getPartialResults) {
        speechRecognitionEngine.setPartialResults(getPartialResults);
        return this;
    }

    /**
     * Sets text to speech and recognition language.
     * Defaults to device language setting.
     *
     * @param locale new locale
     * @return speech instance
     */
    public Speech setLocale(final Locale locale) {
        speechRecognitionEngine.setLocale(locale);
        textToSpeechEngine.setLocale(locale);
        return this;
    }

    /**
     * Sets the speech rate. This has no effect on any pre-recorded speech.
     *
     * @param rate Speech rate. 1.0 is the normal speech rate, lower values slow down the speech
     *             (0.5 is half the normal speech rate), greater values accelerate it
     *             (2.0 is twice the normal speech rate).
     * @return speech instance
     */
    public Speech setTextToSpeechRate(final float rate) {
        textToSpeechEngine.setSpeechRate(rate);
        return this;
    }

    /**
     * Sets the voice for the TextToSpeech engine.
     * This has no effect on any pre-recorded speech.
     *
     * @param voice Speech voice.
     * @return speech instance
     */
    public Speech setVoice(final Voice voice) {
        textToSpeechEngine.setVoice(voice);
        return this;
    }

    /**
     * Sets the speech pitch for the TextToSpeech engine.
     * This has no effect on any pre-recorded speech.
     *
     * @param pitch Speech pitch. 1.0 is the normal pitch, lower values lower the tone of the
     *              synthesized voice, greater values increase it.
     * @return speech instance
     */
    public Speech setTextToSpeechPitch(final float pitch) {
        textToSpeechEngine.setPitch(pitch);
        return this;
    }

    /**
     * Sets the idle timeout after which the listening will be automatically stopped.
     *
     * @param milliseconds timeout in milliseconds
     * @return speech instance
     */
    public Speech setStopListeningAfterInactivity(final long milliseconds) {
        speechRecognitionEngine.setStopListeningAfterInactivity(milliseconds);
        speechRecognitionEngine.init(mContext);
        return this;
    }

    /**
     * Sets the minimum interval between start/stop events. This is useful to prevent
     * monkey input from users.
     *
     * @param milliseconds minimum interval betweeb state change in milliseconds
     * @return speech instance
     */
    public Speech setTransitionMinimumDelay(final long milliseconds) {
        speechRecognitionEngine.setTransitionMinimumDelay(milliseconds);
        return this;
    }

    /**
     * Sets the text to speech queue mode.
     * By default is TextToSpeech.QUEUE_FLUSH, which is faster, because it clears all the
     * messages before speaking the new one. TextToSpeech.QUEUE_ADD adds the last message
     * to speak in the queue, without clearing the messages that have been added.
     *
     * @param mode It can be either TextToSpeech.QUEUE_ADD or TextToSpeech.QUEUE_FLUSH.
     * @return speech instance
     */
    public Speech setTextToSpeechQueueMode(final int mode) {
        textToSpeechEngine.setTextToSpeechQueueMode(mode);
        return this;
    }

    /**
     * Sets the audio stream type.
     * By default is TextToSpeech.Engine.DEFAULT_STREAM, which is equivalent to
     * AudioManager.STREAM_MUSIC.
     *
     * @param audioStream A constant from AudioManager.
     *                    e.g. {@link android.media.AudioManager#STREAM_VOICE_CALL}
     * @return speech instance
     */
    public Speech setAudioStream(final int audioStream) {
        textToSpeechEngine.setAudioStream(audioStream);
        return this;
    }

    private boolean isGoogleAppInstalled() {
        PackageManager packageManager = mContext.getPackageManager();

        for (PackageInfo packageInfo: packageManager.getInstalledPackages(0)) {
            if (packageInfo.packageName.contains(GOOGLE_APP_PACKAGE)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Gets the list of the supported speech to text languages on this device
     * @param listener listner which will receive the results
     */
    public void getSupportedSpeechToTextLanguages(SupportedLanguagesListener listener) {
        if (!isGoogleAppInstalled()) {
            listener.onNotSupported(UnsupportedReason.GOOGLE_APP_NOT_FOUND);
            return;
        }

        Intent intent = RecognizerIntent.getVoiceDetailsIntent(mContext);

        if (intent == null) {
            intent = new Intent(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS);
            intent.setPackage(GOOGLE_APP_PACKAGE);
        }

        mContext.sendOrderedBroadcast(intent, null, new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Bundle extras = getResultExtras(true);

                if (extras != null && extras.containsKey(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES)) {
                    List<String> languages = extras.getStringArrayList(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES);
                    if (languages == null || languages.isEmpty()) {
                        listener.onNotSupported(UnsupportedReason.EMPTY_SUPPORTED_LANGUAGES);
                    } else {
                        Collections.sort(languages);
                        listener.onSupportedLanguages(languages);
                    }
                } else {
                    listener.onNotSupported(UnsupportedReason.EMPTY_SUPPORTED_LANGUAGES);
                }
            }
        }, null, Activity.RESULT_OK, null, null);
    }

    /**
     * Gets the list of the supported Text to Speech languages on this device
     * @return list of locales on android API 23 and newer and empty list on lower Android, because native
     * TTS engine does not support querying voices on API lower than 23. Officially it's declared that
     * query voices support started on API 21, but in reality it started from 23.
     * If still skeptic about this, search the web and try on your own.
     */
    public List<Voice> getSupportedTextToSpeechVoices() {
        return textToSpeechEngine.getSupportedVoices();
    }

    /**
     * Gets the locale used for speech recognition.
     * @return speech recognition locale
     */
    public Locale getSpeechToTextLanguage() {
        return speechRecognitionEngine.getLocale();
    }

    /**
     * Gets the current voice used for text to speech.
     * @return current voice on android API 23 or newer and null on lower Android, because native
     * TTS engine does not support querying voices on API lower than 23. Officially it's declared that
     * query voices support started on API 21, but in reality it started from 23.
     * If still skeptic about this, search the web and try on your own.
     */
    public Voice getTextToSpeechVoice() {
        return textToSpeechEngine.getCurrentVoice();
    }

}
