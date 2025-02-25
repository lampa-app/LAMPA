package net.gotev.speech.engine;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import net.gotev.speech.DelayedOperation;
import net.gotev.speech.SpeechDelegate;
import net.gotev.speech.GoogleVoiceTypingDisabledException;
import net.gotev.speech.SpeechRecognitionException;
import net.gotev.speech.SpeechRecognitionNotAvailable;
import net.gotev.speech.Logger;
import net.gotev.speech.ui.SpeechProgressView;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BaseSpeechRecognitionEngine implements SpeechRecognitionEngine {
    private static final String LOG_TAG = BaseSpeechRecognitionEngine.class.getSimpleName();

    private Context mContext;

    private SpeechRecognizer mSpeechRecognizer;
    private SpeechDelegate mDelegate;
    private SpeechProgressView mProgressView;
    private String mCallingPackage;

    private String mUnstableData;

    private DelayedOperation mDelayedStopListening;
    private final List<String> mPartialData = new ArrayList<>();
    private List<String> mLastPartialResults = null;

    private Locale mLocale = Locale.getDefault();
    private boolean mPreferOffline = false;
    private boolean mGetPartialResults = true;
    private boolean mIsListening = false;
    private long mLastActionTimestamp;
    private long mStopListeningDelayInMs = 4000;
    private long mTransitionMinimumDelay = 1200;

    @Override
    public void init(Context context) {
        initDelayedStopListening(context);
    }

    @Override
    public void clear() {
        mPartialData.clear();
        mUnstableData = null;
    }

    @Override
    public void onReadyForSpeech(final Bundle bundle) {
        mPartialData.clear();
        mUnstableData = null;
    }

    @Override
    public void onBeginningOfSpeech() {
        if (mProgressView != null)
            mProgressView.onBeginningOfSpeech();

        mDelayedStopListening.start(new DelayedOperation.Operation() {
            @Override
            public void onDelayedOperation() {
                returnPartialResultsAndRecreateSpeechRecognizer();
            }

            @Override
            public boolean shouldExecuteDelayedOperation() {
                return true;
            }
        });
    }

    @Override
    public void onRmsChanged(final float v) {
        try {
            if (mDelegate != null)
                mDelegate.onSpeechRmsChanged(v);
        } catch (final Throwable exc) {
            Logger.error(getClass().getSimpleName(),
                    "Unhandled exception in delegate onSpeechRmsChanged", exc);
        }

        if (mProgressView != null)
            mProgressView.onRmsChanged(v);
    }

    @Override
    public void onPartialResults(final Bundle bundle) {
        mDelayedStopListening.resetTimer();

        final List<String> partialResults = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        final List<String> unstableData = bundle.getStringArrayList("android.speech.extra.UNSTABLE_TEXT");

        if (partialResults != null && !partialResults.isEmpty()) {
            mPartialData.clear();
            mPartialData.addAll(partialResults);
            mUnstableData = unstableData != null && !unstableData.isEmpty()
                    ? unstableData.get(0) : null;
            try {
                if (mLastPartialResults == null || !mLastPartialResults.equals(partialResults)) {
                    if (mDelegate != null)
                        mDelegate.onSpeechPartialResults(partialResults);
                    mLastPartialResults = partialResults;
                }
            } catch (final Throwable exc) {
                Logger.error(getClass().getSimpleName(),
                        "Unhandled exception in delegate onSpeechPartialResults", exc);
            }
        }
    }

    @Override
    public void onResults(final Bundle bundle) {
        mDelayedStopListening.cancel();

        final List<String> results = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

        final String result;

        if (results != null && !results.isEmpty()
                && results.get(0) != null && !results.get(0).isEmpty()) {
            result = results.get(0);
        } else {
            Logger.info(getClass().getSimpleName(), "No speech results, getting partial");
            result = getPartialResultsAsString();
        }

        mIsListening = false;

        try {
            if (mDelegate != null)
                mDelegate.onSpeechResult(result.trim());
        } catch (final Throwable exc) {
            Logger.error(getClass().getSimpleName(),
                    "Unhandled exception in delegate onSpeechResult", exc);
        }

        if (mProgressView != null)
            mProgressView.onResultOrOnError();

        initSpeechRecognizer(mContext);
    }

    @Override
    public void onError(final int code) {
        Logger.error(LOG_TAG, "Speech recognition error", new SpeechRecognitionException(code));
        returnPartialResultsAndRecreateSpeechRecognizer();
    }

    @Override
    public void onBufferReceived(final byte[] bytes) {

    }

    @Override
    public void onEndOfSpeech() {
        if (mProgressView != null)
            mProgressView.onEndOfSpeech();
    }

    @Override
    public void onEvent(final int i, final Bundle bundle) {

    }

    @Override
    public String getPartialResultsAsString() {
        final StringBuilder out = new StringBuilder("");

        for (final String partial : mPartialData) {
            out.append(partial).append(" ");
        }

        if (mUnstableData != null && !mUnstableData.isEmpty())
            out.append(mUnstableData);

        return out.toString().trim();
    }

    @Override
    public void startListening(SpeechProgressView progressView, SpeechDelegate delegate) throws SpeechRecognitionNotAvailable, GoogleVoiceTypingDisabledException {
        if (mIsListening) return;

        if (mSpeechRecognizer == null)
            throw new SpeechRecognitionNotAvailable();

        if (delegate == null)
            throw new IllegalArgumentException("delegate must be defined!");

        if (throttleAction()) {
            Logger.debug(getClass().getSimpleName(), "Hey man calm down! Throttling start to prevent disaster!");
            return;
        }

        mProgressView = progressView;
        mDelegate = delegate;

        if (progressView != null && !(progressView.getParent() instanceof LinearLayout))
            throw new IllegalArgumentException("progressView must be put inside a LinearLayout!");
//        Log.d("*****", "mLocale.getLanguage(): " + mLocale.getLanguage());
//        Log.d("*****", "getIETFLangTag: " + getIETFLangTag());
        final Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, mGetPartialResults)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, getIETFLangTag())
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

        if (mCallingPackage != null && !mCallingPackage.isEmpty()) {
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, mCallingPackage);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, mPreferOffline);
        }
//        Log.d("*****", "Recoginizer intent: " + intent.toUri(0));
        try {
            mSpeechRecognizer.startListening(intent);
        } catch (final SecurityException exc) {
            throw new GoogleVoiceTypingDisabledException();
        }

        mIsListening = true;
        updateLastActionTimestamp();

        try {
            if (mDelegate != null)
                mDelegate.onStartOfSpeech();
        } catch (final Throwable exc) {
            Logger.error(getClass().getSimpleName(),
                    "Unhandled exception in delegate onStartOfSpeech", exc);
        }
    }

    @Nullable
    private String getIETFLangTag() {
        String lang = mLocale.getLanguage();
        if (lang.isEmpty()) {
            return "en-US"; // Handle null or empty language
        }
        if (!lang.contains("-")) if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            lang = mLocale.toLanguageTag();
        } else {
            Map<String, String> languageToLocaleMap = new HashMap<>();
            languageToLocaleMap.put("ru", "ru-RU");
            languageToLocaleMap.put("en", "en-US");
            languageToLocaleMap.put("be", "be-BY");
            languageToLocaleMap.put("uk", "uk-UA");
            languageToLocaleMap.put("zh", "zh-CN");
            languageToLocaleMap.put("bg", "bg-BG");
            languageToLocaleMap.put("pt", "pt-PT");
            languageToLocaleMap.put("cs", "cs-CZ");
            lang = languageToLocaleMap.getOrDefault(lang, lang);
        }
        return lang;
    }

    @Override
    public boolean isListening() {
        return mIsListening;
    }

    @Override
    public Locale getLocale() {
        return mLocale;
    }

    @Override
    public void setLocale(Locale locale) {
        mLocale = locale;
    }

    @Override
    public void stopListening() {
        if (!mIsListening) return;

        if (throttleAction()) {
            Logger.debug(getClass().getSimpleName(), "Hey man calm down! Throttling stop to prevent disaster!");
            return;
        }

        mIsListening = false;
        updateLastActionTimestamp();
        returnPartialResultsAndRecreateSpeechRecognizer();
    }

    public void initSpeechRecognizer(final Context context) {
        if (context == null)
            throw new IllegalArgumentException("context must be defined!");

        mContext = context;

        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            if (mSpeechRecognizer != null) {
                try {
                    mSpeechRecognizer.destroy();
                } catch (final Throwable exc) {
                    Logger.debug(getClass().getSimpleName(),
                            "Non-Fatal error while destroying speech. " + exc.getMessage());
                } finally {
                    mSpeechRecognizer = null;
                }
            }

            mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            mSpeechRecognizer.setRecognitionListener(this);
            init(context);

        } else {
            mSpeechRecognizer = null;
        }

        clear();
    }

    @Override
    public void returnPartialResultsAndRecreateSpeechRecognizer() {
        mIsListening = false;
        try {
            if (mDelegate != null)
                mDelegate.onSpeechResult(getPartialResultsAsString());
        } catch (final Throwable exc) {
            Logger.error(getClass().getSimpleName(),
                    "Unhandled exception in delegate onSpeechResult", exc);
        }

        if (mProgressView != null)
            mProgressView.onResultOrOnError();

        initSpeechRecognizer(mContext);
    }

    @Override
    public void setPartialResults(boolean getPartialResults) {
        this.mGetPartialResults = getPartialResults;
    }

    @Override
    public void unregisterDelegate() {
        mProgressView = null;
        mDelegate = null;
    }

    @Override
    public void setPreferOffline(boolean preferOffline) {
        mPreferOffline = preferOffline;
    }

    private void initDelayedStopListening(final Context context) {
        if (mDelayedStopListening != null) {
            mDelayedStopListening.cancel();
            mDelayedStopListening = null;
            stopDueToDelay();
        }

        mDelayedStopListening = new DelayedOperation(context, "delayStopListening", mStopListeningDelayInMs);
    }

    protected void stopDueToDelay() {

    }

    private void updateLastActionTimestamp() {
        mLastActionTimestamp = new Date().getTime();
    }

    private boolean throttleAction() {
        return (new Date().getTime() <= (mLastActionTimestamp + mTransitionMinimumDelay));
    }

    @Override
    public void setCallingPackage(String callingPackage) {
        this.mCallingPackage = callingPackage;
    }

    @Override
    public void setTransitionMinimumDelay(long milliseconds) {
        this.mTransitionMinimumDelay = milliseconds;
    }

    @Override
    public void setStopListeningAfterInactivity(long milliseconds) {
        this.mStopListeningDelayInMs = milliseconds;
    }

    @Override
    public void shutdown() {
        if (mSpeechRecognizer != null) {
            try {
                mSpeechRecognizer.stopListening();
                mSpeechRecognizer.destroy();
            } catch (final Exception exc) {
                Logger.error(getClass().getSimpleName(), "Warning while de-initing speech recognizer", exc);
            }
        }

        unregisterDelegate();
    }
}
