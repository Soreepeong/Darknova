package com.soreepeong.darknova.ui.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ToggleButton;

import com.soreepeong.darknova.R;
import com.soreepeong.darknova.core.ImageCache;
import com.soreepeong.darknova.core.ResTools;
import com.soreepeong.darknova.settings.NewTweet;
import com.soreepeong.darknova.twitter.Tweet;
import com.soreepeong.darknova.twitter.Tweeter;
import com.soreepeong.darknova.twitter.TwitterEngine;

import org.apmem.tools.layouts.FlowLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Soreepeong on 2015-05-05.
 */
public class NewTweetFragment extends Fragment implements Tweeter.OnUserInformationChangedListener, ImageCache.OnImageCacheReadyListener, CompoundButton.OnCheckedChangeListener, View.OnClickListener, TwitterEngine.OnUserlistChangedListener, TextWatcher{

	private static final String PREFERENCE_KEY_NAME = "newTweetFragment-input";

	private View mViewNewTweet;
	private Button mViewClearBtn;
	private ImageButton mViewWriteBtn, mViewAttachBtn;
	private EditText mViewEditor;
	private ImageCache mImageCache;
	private OnNewTweetVisibilityChangedListener mListener;
	private Tweet mInReplyTo;
	private HashMap<Tweeter, ToggleButton> mUserMaps;
	private FlowLayout mViewAccount;
	private boolean mIsShown, mIsActionbarShown = true;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mImageCache = ImageCache.getCache(getActivity(), this);
		mViewNewTweet = inflater.inflate(R.layout.fragment_newtweet, container, false);
		mViewWriteBtn = (ImageButton) mViewNewTweet.findViewById(R.id.write_btn);
		mViewClearBtn = (Button) mViewNewTweet.findViewById(R.id.clear_btn);
		mViewAttachBtn = (ImageButton) mViewNewTweet.findViewById(R.id.attach_btn);
		mViewEditor = (EditText) mViewNewTweet.findViewById(R.id.editor);
		mViewAccount = (FlowLayout) mViewNewTweet.findViewById(R.id.account_list);
		mUserMaps = new HashMap<>();
		refillUserMaps(TwitterEngine.getTwitterEngines(getActivity()));
		mViewNewTweet.setVisibility(View.GONE);
		mViewWriteBtn.setOnClickListener(this);
		mViewClearBtn.setOnClickListener(this);
		mViewAttachBtn.setOnClickListener(this);
		mViewEditor.addTextChangedListener(this);
		TwitterEngine.addOnUserlistChangedListener(this);
		SharedPreferences pref = getActivity().getPreferences(Context.MODE_PRIVATE);
		readFromNewTweet(new NewTweet(PREFERENCE_KEY_NAME, pref));
		if(pref.getBoolean(PREFERENCE_KEY_NAME + ".ui.open", false))
			showNewTweet();
		return mViewNewTweet;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		for(Tweeter t : mUserMaps.keySet()){
			t.removeOnChangeListener(this);
		}
		TwitterEngine.removeOnUserlistChangedListener(this);
		mImageCache = null;
	}

	public void refillUserMaps(ArrayList<TwitterEngine> engines){
		ArrayList<Tweeter> selected = new ArrayList<>();
		for(Tweeter t : mUserMaps.keySet()){
			if(mUserMaps.get(t).isChecked())
				selected.add(t);
			t.removeOnChangeListener(this);
		}
		mUserMaps.clear();
		mViewAccount.removeAllViews();
		for(TwitterEngine engine : engines){
			Tweeter t = engine.getTweeter();
			ToggleButton box = (ToggleButton) getActivity().getLayoutInflater().inflate(R.layout.fragment_newtweet_user, mViewAccount, false);
			box.setChecked(selected.contains(t));
			box.setTextOff(t.screen_name);
			box.setTextOn(t.screen_name);
			box.setText(t.screen_name);
			t.addToDataLoadQueue(engine);
			Drawable dr = (mImageCache == null || t.getProfileImageUrl() == null) ? new ColorDrawable(0xFF00FF00) : mImageCache.getDrawable(t.getProfileImageUrl(), R.dimen.account_button_icon_size, null);
			dr.setAlpha(box.isChecked() ? 255 : 80);
			box.setCompoundDrawablesWithIntrinsicBounds(dr, null, null, null);
			box.setOnCheckedChangeListener(this);
			mViewAccount.addView(box);
			mUserMaps.put(t, box);
			t.addOnChangeListener(this);
		}
		Tweeter.fillLoadQueuedData();
	}

	@Override
	public void onUserInformationChanged(Tweeter tweeter) {
		ToggleButton box = mUserMaps.get(tweeter);
		if(box == null) return;
		Drawable dr = (mImageCache == null || tweeter.getProfileImageUrl() == null) ? new ColorDrawable(0xFF00FF00) : mImageCache.getDrawable(tweeter.getProfileImageUrl(), R.dimen.account_button_icon_size, null);
		dr.setAlpha(box.isChecked() ? 255 : 80);
		box.setCompoundDrawablesWithIntrinsicBounds(dr, null, null, null);
	}

	public void onImageCacheReady(ImageCache cache) {
		mImageCache = cache;
		refillUserMaps(TwitterEngine.getTwitterEngines(getActivity()));
	}

	public void setOnNewTweetVisibilityChangedListener(OnNewTweetVisibilityChangedListener listener) {
		mListener = listener;
	}

	public void setInReplyTo(Tweet t){
		mInReplyTo = t;
	}

	public void showWithActionBar(){
		mIsActionbarShown = true;
		if(mIsShown){
			if (mViewNewTweet.getVisibility() == View.VISIBLE) return;
			mViewNewTweet.setVisibility(View.VISIBLE);
			mViewNewTweet.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.newtweet_show));
			mViewEditor.requestFocus();
		}else
			mListener.onNewTweetVisibilityChanged(false);
	}

	public void hideWithActionBar(){
		mIsActionbarShown = false;
		mListener.onNewTweetVisibilityChanged(true);
		if (mViewNewTweet.getVisibility() == View.GONE) return;
		ResTools.hideWithAnimation(getActivity(), mViewNewTweet, R.anim.newtweet_hide);
	}

	public void showNewTweet() {
		mIsShown = true;
		if(!mIsActionbarShown)
			return;
		if (mViewNewTweet.getVisibility() == View.VISIBLE) return;
		mViewNewTweet.setVisibility(View.VISIBLE);

		mViewNewTweet.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.newtweet_show));
		mViewEditor.requestFocus();
		if (mListener != null)
			mListener.onNewTweetVisibilityChanged(true);

		// open keyboard
		InputMethodManager mgr = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
		mgr.showSoftInput(mViewEditor, InputMethodManager.SHOW_IMPLICIT);
		((InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(mViewEditor, 0);
	}

	public void hideNewTweet() {
		mIsShown = false;
		if (mViewNewTweet.getVisibility() == View.GONE) return;
		ResTools.hideWithAnimation(getActivity(), mViewNewTweet, R.anim.newtweet_hide);
		if (mListener != null)
			mListener.onNewTweetVisibilityChanged(false);

		InputMethodManager imm = (InputMethodManager)getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(getActivity().getWindow().getDecorView().getWindowToken(), 0);
	}

	public boolean isNewTweetVisible() {
		return mViewNewTweet.getVisibility() == View.VISIBLE;
	}

	@Override
	public void onCheckedChanged(CompoundButton v, boolean isChecked) {
		v.getCompoundDrawables()[0].setAlpha(isChecked ? 255 : 80);
	}

	public void insertText(String sText){
		insertText(sText, mViewEditor.getSelectionStart(), mViewEditor.getSelectionEnd());
	}

	public void insertText(String sText, int nStart, int nEnd){
		if(sText.length()==0) return;
		Editable edt = mViewEditor.getText();
		if(nStart > 0 && edt.subSequence(nStart - 1, nStart).toString().matches("[A-Za-z0-9_]") && !sText.matches("^\\s.*$"))
			sText = " " + sText;
		if((nEnd >= edt.length() - 1 || (nEnd < edt.length() - 1 && edt.subSequence(nEnd, nEnd + 1).toString().matches("[A-Za-z0-9_]"))) && !sText.matches("^.*\\s$"))
			sText = sText + " ";
		edt = edt.replace(nStart, nEnd, sText);
		mViewEditor.setText(edt);
		mViewEditor.setSelection(nStart + sText.length());
	}

	public ArrayList<TwitterEngine> getPostFromList(){
		ArrayList<TwitterEngine> postFrom=new ArrayList<>();
		for(Tweeter ttr : mUserMaps.keySet())
			if(mUserMaps.get(ttr).isChecked())
				postFrom.add(TwitterEngine.getTwitterEngine(getActivity(), ttr.user_id));
		return postFrom;
	}

	public ArrayList<Long> getPostFromIdList(){
		ArrayList<Long> postFrom=new ArrayList<>();
		for(Tweeter ttr : mUserMaps.keySet())
			if(mUserMaps.get(ttr).isChecked())
				postFrom.add(ttr.user_id);
		return postFrom;
	}

	@Override
	public void onClick(View v) {
		if(v.equals(mViewWriteBtn)){
			if(mViewEditor.getText().length()==0)
				hideNewTweet();
			else{
				String t = mViewEditor.getText().toString();
				if(t.length()>140) t = t.substring(0, 140);
				final ArrayList<TwitterEngine> postFrom=getPostFromList();
				final String postText = t;
				final long replyto=mInReplyTo!=null?mInReplyTo.id:0;
				new Thread(){
					@Override
					public void run() {
						try{
							for (TwitterEngine e : postFrom)
								e.postTweet(postText, replyto, 0, 0, false, null);
						}catch(Exception e){
							e.printStackTrace();
						}
					}
				}.start();
				mViewEditor.setText("");
			}
		}else if(v.equals(mViewClearBtn)){
			if(mViewEditor.getText().length()==0)
				hideNewTweet();
			else{
				new AlertDialog.Builder(getActivity())
						.setIcon(android.R.drawable.ic_dialog_alert)
						.setMessage(R.string.new_tweet_clear_confirm)
						.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								mViewEditor.setText("");
								mInReplyTo=null;
							}
						})
						.setNegativeButton(android.R.string.no, null)
						.show();
			}
		}
	}

	private void readFromNewTweet(NewTweet t){
		mInReplyTo = Tweet.getTweet(t.mInReplyTo);
		if(t.mText != null){
			mViewEditor.setText(t.mText);
			if(t.mSelectionStart > t.mText.length())
				t.mSelectionStart = t.mText.length();
			if(t.mSelectionEnd < t.mSelectionStart)
				t.mSelectionEnd = t.mSelectionStart;
			mViewEditor.setSelection(t.mSelectionStart, t.mSelectionEnd);
		}
		if(t.mUserIdList != null && !t.mUserIdList.isEmpty()){
			for(Tweeter ttr : mUserMaps.keySet()){
				mUserMaps.get(ttr).setChecked(false);
				for(Long id : t.mUserIdList)
					if(ttr.user_id == id)
						mUserMaps.get(ttr).setChecked(true);
			}
		}
	}

	private NewTweet getNewTweet(){
		NewTweet tweet = new NewTweet();
		tweet.mInReplyTo = mInReplyTo == null ? 0: mInReplyTo.id;
		tweet.mText = mViewEditor.getText().toString();
		tweet.mUserIdList.addAll(getPostFromIdList());
		tweet.mSelectionStart = mViewEditor.getSelectionStart();
		tweet.mSelectionEnd = mViewEditor.getSelectionEnd();
		return tweet;
	}

	@Override
	public void onPause() {
		super.onPause();
		SharedPreferences.Editor editor = getActivity().getPreferences(Context.MODE_PRIVATE).edit();
		getNewTweet().writeToPreferences(PREFERENCE_KEY_NAME, editor);
		editor.putBoolean(PREFERENCE_KEY_NAME + ".ui.open", mIsShown);
		editor.apply();
	}

	@Override
	public void onUserlistChanged(List<TwitterEngine.StreamableTwitterEngine> engines) {
		ArrayList<TwitterEngine> newEngines = new ArrayList<>();
		newEngines.addAll(engines);
		refillUserMaps(newEngines);
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {

	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {

	}

	@Override
	public void afterTextChanged(Editable s) {
		if(s.toString().trim().isEmpty())
			mViewClearBtn.setText(R.string.new_tweet_clear);
		else
			mViewClearBtn.setText(s.toString().trim().length() + "/" + 140);
	}

	public interface OnNewTweetVisibilityChangedListener {
		public void onNewTweetVisibilityChanged(boolean visible);
	}
}
