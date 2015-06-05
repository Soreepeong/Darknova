package com.soreepeong.darknova.ui.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
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
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.soreepeong.darknova.DarknovaApplication;
import com.soreepeong.darknova.R;
import com.soreepeong.darknova.core.ImageCache;
import com.soreepeong.darknova.settings.TemplateTweet;
import com.soreepeong.darknova.settings.TemplateTweetAttachment;
import com.soreepeong.darknova.tools.ResTools;
import com.soreepeong.darknova.twitter.Tweet;
import com.soreepeong.darknova.twitter.Tweeter;
import com.soreepeong.darknova.twitter.TwitterEngine;

import org.apmem.tools.layouts.FlowLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * New template tweet maker.
 *
 * @author Soreepeong
 */
public class NewTemplateTweetFragment extends Fragment implements Tweeter.OnUserInformationChangedListener, ImageCache.OnImageCacheReadyListener, CompoundButton.OnCheckedChangeListener, View.OnClickListener, TwitterEngine.OnUserlistChangedListener, TextWatcher {

	private static final int PICK_MEDIA = 1;

	private View mViewNewTweet;
	private Button mViewClearBtn, mViewTypeSelectBtn;
	private FrameLayout mViewUserSelectBtn;
	private TextView mViewUserSelectedText;
	private ImageView mViewUserSelectExpander;
	private ImageButton mViewWriteBtn;
	private EditText mViewEditor;
	private ImageCache mImageCache;
	private OnNewTweetVisibilityChangedListener mListener;
	private Tweet mInReplyTo;
	private HashMap<Tweeter, ToggleButton> mUserMaps;
	private FlowLayout mViewAccount;
	private RecyclerView mViewAttachList, mViewSelectedUserList;
	private boolean mIsShown, mIsActionbarShown = true;
	private AttachmentAdapter mAttachmentAdapter;
	private LinearLayoutManager mAttachmentLayoutManager;
	private UserImageAdapter mUserAdapter;
	private GridLayoutManager mUserListLayoutManager;

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
		mViewUserSelectBtn = (FrameLayout) mViewNewTweet.findViewById(R.id.user_select);
		mViewUserSelectExpander = (ImageView) mViewNewTweet.findViewById(R.id.user_select_expander);
		mViewUserSelectedText = (TextView) mViewNewTweet.findViewById(R.id.user_select_text);
		mViewSelectedUserList = (RecyclerView) mViewNewTweet.findViewById(R.id.selected_user_image_list);
		mViewTypeSelectBtn = (Button) mViewNewTweet.findViewById(R.id.type_select);
		mViewAttachList = (RecyclerView) mViewNewTweet.findViewById(R.id.attach_list);
		mViewEditor = (EditText) mViewNewTweet.findViewById(R.id.editor);
		mViewAccount = (FlowLayout) mViewNewTweet.findViewById(R.id.account_list);
		mUserMaps = new HashMap<>();
		refillUserMaps(TwitterEngine.getAll());
		mViewNewTweet.setVisibility(View.GONE);
		mViewWriteBtn.setOnClickListener(this);
		mViewClearBtn.setOnClickListener(this);
		mViewUserSelectBtn.setOnClickListener(this);
		mViewTypeSelectBtn.setOnClickListener(this);
		mViewEditor.addTextChangedListener(this);
		TwitterEngine.addOnUserlistChangedListener(this);
		mViewSelectedUserList.setLayoutManager(mUserListLayoutManager = new GridLayoutManager(getActivity(), 1));
		mViewSelectedUserList.setAdapter(mUserAdapter = new UserImageAdapter());
		mViewSelectedUserList.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
			@Override
			public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
				mUserAdapter.invalidate();
			}
		});
		mViewSelectedUserList.addItemDecoration(new RecyclerView.ItemDecoration() {
			@Override
			public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
				outRect.set(0, 0, 0, 0);
			}
		});
		mViewAttachList.setLayoutManager(mAttachmentLayoutManager = new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false));
		mViewAttachList.setAdapter(mAttachmentAdapter = new AttachmentAdapter());
		mAttachmentLayoutManager.setStackFromEnd(true);
		readFromNewTweet(TemplateTweet.getEditorTweet());
		if (savedInstanceState != null && savedInstanceState.getBoolean("open"))
			showNewTweet();
		return mViewNewTweet;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		for (Tweeter t : mUserMaps.keySet()) {
			t.removeOnChangeListener(this);
		}
		TwitterEngine.removeOnUserlistChangedListener(this);
		mImageCache = null;
	}

	private void refillUserMaps(ArrayList<TwitterEngine> engines) {
		ArrayList<Tweeter> selected = new ArrayList<>();
		for (Tweeter t : mUserMaps.keySet()) {
			if (mUserMaps.get(t).isChecked())
				selected.add(t);
			t.removeOnChangeListener(this);
		}
		mUserMaps.clear();
		mViewAccount.removeAllViews();
		for (TwitterEngine engine : engines) {
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
		if (box == null) return;
		Drawable dr = (mImageCache == null || tweeter.getProfileImageUrl() == null) ? new ColorDrawable(0xFF00FF00) : mImageCache.getDrawable(tweeter.getProfileImageUrl(), R.dimen.account_button_icon_size, null);
		dr.setAlpha(box.isChecked() ? 255 : 80);
		box.setCompoundDrawablesWithIntrinsicBounds(dr, null, null, null);
	}

	public void onImageCacheReady(ImageCache cache) {
		mImageCache = cache;
		refillUserMaps(TwitterEngine.getAll());
		mAttachmentAdapter.notifyDataSetChanged();
		mUserAdapter.notifyDataSetChanged();
	}

	public void setOnNewTweetVisibilityChangedListener(OnNewTweetVisibilityChangedListener listener) {
		mListener = listener;
	}

	public void setInReplyTo(Tweet t) {
		mInReplyTo = t;
	}

	public void showWithActionBar() {
		mIsActionbarShown = true;
		if (mIsShown) {
			if (mViewNewTweet.getVisibility() == View.VISIBLE) return;
			mViewNewTweet.setVisibility(View.VISIBLE);
			mViewNewTweet.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.show_upward));
			mViewEditor.requestFocus();
		} else
			mListener.onNewTweetVisibilityChanged(false);
	}

	public void hideWithActionBar() {
		mIsActionbarShown = false;
		if (mListener != null)
			mListener.onNewTweetVisibilityChanged(true);
		if (mViewNewTweet.getVisibility() != View.VISIBLE) return;
		ResTools.hideWithAnimation(getActivity(), mViewNewTweet, R.anim.hide_downward, true);
	}

	public void showNewTweet() {
		mIsShown = true;
		if (!mIsActionbarShown)
			return;
		if (mViewNewTweet.getVisibility() == View.VISIBLE) return;
		mViewNewTweet.setVisibility(View.VISIBLE);

		mViewNewTweet.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.show_upward));
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
		if (mViewNewTweet.getVisibility() != View.VISIBLE) return;
		ResTools.hideWithAnimation(getActivity(), mViewNewTweet, R.anim.hide_downward, true);
		if (mListener != null)
			mListener.onNewTweetVisibilityChanged(false);

		InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(getActivity().getWindow().getDecorView().getWindowToken(), 0);
	}

	public boolean isNewTweetVisible() {
		return mViewNewTweet.getVisibility() == View.VISIBLE;
	}

	@Override
	public void onCheckedChanged(CompoundButton v, boolean isChecked) {
		v.getCompoundDrawables()[0].setAlpha(isChecked ? 255 : 80);
		applyUserText();
	}

	public void insertText(String sText) {
		insertText(sText, mViewEditor.getSelectionStart(), mViewEditor.getSelectionEnd());
	}

	public void insertText(String sText, int nStart, int nEnd) {
		if (sText.length() == 0) return;
		Editable edt = mViewEditor.getText();
		if (nStart > 0 && edt.subSequence(nStart - 1, nStart).toString().matches("[A-Za-z0-9_]") && !sText.matches("^\\s.*$"))
			sText = " " + sText;
		if ((nEnd >= edt.length() - 1 || (nEnd < edt.length() - 1 && edt.subSequence(nEnd, nEnd + 1).toString().matches("[A-Za-z0-9_]"))) && !sText.matches("^.*\\s$"))
			sText = sText + " ";
		edt = edt.replace(nStart, nEnd, sText);
		mViewEditor.setText(edt);
		mViewEditor.setSelection(nStart + sText.length());
	}

	public ArrayList<TwitterEngine> getPostFromList() {
		ArrayList<TwitterEngine> postFrom = new ArrayList<>();
		for (Tweeter ttr : mUserMaps.keySet())
			if (mUserMaps.get(ttr).isChecked())
				postFrom.add(TwitterEngine.get(ttr.user_id));
		return postFrom;
	}

	public ArrayList<Long> getPostFromIdList() {
		ArrayList<Long> postFrom = new ArrayList<>();
		for (Tweeter ttr : mUserMaps.keySet())
			if (mUserMaps.get(ttr).isChecked())
				postFrom.add(ttr.user_id);
		return postFrom;
	}

	@Override
	public void onClick(View v) {
		if (v.equals(mViewWriteBtn)) {
			if (mViewEditor.getText().length() == 0 && mAttachmentAdapter.mAttachments.isEmpty())
				hideNewTweet();
			else {
				final TemplateTweet t = getNewTweet();
				mViewEditor.setText("");
				// mAttachmentAdapter.removeAll();
				mInReplyTo = null;
				if (t.mText.length() > 140) t.mText = t.mText.substring(0, 140);
				final ArrayList<TwitterEngine> postFrom = getPostFromList();
				new Thread() {
					@Override
					public void run() {
						try {
							for (TwitterEngine e : postFrom) {
								ArrayList<Long> mediaIds = new ArrayList<>();
								for (TemplateTweetAttachment a : t.mAttachments)
									mediaIds.add(a.media_id = e.uploadMedia(new File(a.mLocalPath)));
								e.postTweet(t.mText, t.mInReplyTo, 0, 0, false, mediaIds.isEmpty() ? null : mediaIds);
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}.start();
				mViewEditor.setText("");
			}
		} else if (v.equals(mViewClearBtn)) {
			if (mViewEditor.getText().length() == 0 && mAttachmentAdapter.mAttachments.isEmpty())
				hideNewTweet();
			else {
				new AlertDialog.Builder(getActivity())
						.setIcon(android.R.drawable.ic_dialog_alert)
						.setMessage(R.string.new_tweet_clear_confirm)
						.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								mViewEditor.setText("");
								mAttachmentAdapter.removeAll();
								mInReplyTo = null;
							}
						})
						.setNegativeButton(android.R.string.no, null)
						.show();
			}
		} else if (v.equals(mViewUserSelectBtn)) {
			if (mViewAccount.getVisibility() != View.VISIBLE) {
				mViewAccount.setVisibility(View.VISIBLE);
				mViewAccount.clearAnimation();
				mViewAccount.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.show_upward));
			} else
				ResTools.hideWithAnimation(getActivity(), mViewAccount, R.anim.hide_downward, false);
			mViewUserSelectExpander.setImageDrawable(ResTools.getDrawableByAttribute(getActivity(), mViewAccount.getVisibility() != View.VISIBLE ? R.attr.ic_navigation_expand_less : R.attr.ic_navigation_expand_more));
		} else if (v.equals(mViewTypeSelectBtn)) {
			ResTools.hideWithAnimation(getActivity(), mViewAccount, R.anim.hide_downward, false);
		}
	}

	private void applyUserText() {
		ArrayList<TwitterEngine> postFrom = getPostFromList();
		Collections.sort(postFrom);
		mViewUserSelectedText.setVisibility(postFrom.size() <= 1 ? View.VISIBLE : View.GONE);
		mViewUserSelectedText.setText(postFrom.size() == 0 ? getString(R.string.new_tweet_select_user) : postFrom.get(0).getScreenName());
		mUserAdapter.updateList(postFrom);
	}

	private void readFromNewTweet(TemplateTweet t) {
		mInReplyTo = Tweet.getTweet(t.mInReplyTo);
		if (t.mText != null) {
			mViewEditor.setText(t.mText);
			if (t.mSelectionStart > t.mText.length())
				t.mSelectionStart = t.mText.length();
			if (t.mSelectionEnd < t.mSelectionStart)
				t.mSelectionEnd = t.mSelectionStart;
			mViewEditor.setSelection(t.mSelectionStart, t.mSelectionEnd);
		}
		if (t.mUserIdList != null && !t.mUserIdList.isEmpty()) {
			for (Tweeter ttr : mUserMaps.keySet()) {
				mUserMaps.get(ttr).setChecked(false);
				for (Long id : t.mUserIdList)
					if (ttr.user_id == id)
						mUserMaps.get(ttr).setChecked(true);
			}
		}
		applyUserText();
		mAttachmentAdapter.setData(t.mAttachments);
	}

	private TemplateTweet getNewTweet() {
		TemplateTweet tweet = new TemplateTweet();
		getNewTweet(tweet);
		return tweet;
	}

	private void getNewTweet(TemplateTweet tweet) {
		tweet.mInReplyTo = mInReplyTo == null ? 0 : mInReplyTo.id;
		tweet.mText = mViewEditor.getText().toString();
		tweet.mUserIdList.clear();
		tweet.mUserIdList.addAll(getPostFromIdList());
		tweet.mSelectionStart = mViewEditor.getSelectionStart();
		tweet.mSelectionEnd = mViewEditor.getSelectionEnd();
		tweet.mAttachments.clear();
		tweet.mAttachments.addAll(mAttachmentAdapter.mAttachments);
	}

	@Override
	public void onPause() {
		super.onPause();
		getNewTweet(TemplateTweet.getEditorTweet());
		TemplateTweet.saveEditorTweet();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean("open", mIsShown);
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
		if (s.toString().trim().isEmpty())
			mViewClearBtn.setText(R.string.new_tweet_clear);
		else
			mViewClearBtn.setText(s.toString().trim().length() + "/" + 140);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == PICK_MEDIA && resultCode == Activity.RESULT_OK) {
			TemplateTweetAttachment a = new TemplateTweetAttachment(data.getData());
			mAttachmentAdapter.insertItem(a);
			a.resolve(getActivity(), mAttachmentAdapter);
		}
	}

	public interface OnNewTweetVisibilityChangedListener {
		void onNewTweetVisibilityChanged(boolean visible);
	}

	private class UserImageAdapter extends RecyclerView.Adapter<UserImageAdapter.ViewHolder> {
		private final ArrayList<TwitterEngine> mTweeters = new ArrayList<>();
		int itemSizeScale = 1;
		int scale = mViewSelectedUserList.getHeight() == 0 ? mViewSelectedUserList.getLayoutParams().width / mViewSelectedUserList.getLayoutParams().height : mViewSelectedUserList.getWidth() / mViewSelectedUserList.getHeight();

		public UserImageAdapter() {
			setHasStableIds(true);
		}

		public void updateList(final ArrayList<TwitterEngine> newList) {
			ArrayList<TwitterEngine> mRemovingList = new ArrayList<>(newList);
			for (int i = 0; i < mTweeters.size(); i++) {
				boolean removed = true;
				TwitterEngine t = mTweeters.get(i);
				for (TwitterEngine e : mRemovingList)
					if (e.equals(t)) {
						removed = false;
						mRemovingList.remove(e);
						break;
					}
				if (removed) {
					mTweeters.remove(i);
					notifyItemRemoved(i);
					i--;
				}
			}
			for (TwitterEngine e : mRemovingList) {
				int i = Collections.binarySearch(mTweeters, e);
				if (i < 0) {
					i = -i - 1;
					mTweeters.add(i, e);
					notifyItemInserted(i);
				}
			}
			invalidate();
		}

		public void invalidate() {
			scale = Math.max(1, mViewSelectedUserList.getHeight() == 0 ? mViewSelectedUserList.getLayoutParams().width / mViewSelectedUserList.getLayoutParams().height : mViewSelectedUserList.getWidth() / mViewSelectedUserList.getHeight());
			int lastScale = itemSizeScale;
			if (mTweeters.size() <= scale) {
				mUserListLayoutManager.setSpanCount(scale);
				itemSizeScale = 1;
			} else if (mTweeters.size() <= scale * 2) {
				mUserListLayoutManager.setSpanCount(mTweeters.size());
				itemSizeScale = 1;
			} else {
				int i;
				for (i = 2; i * i * scale < mTweeters.size(); i++) ;
				itemSizeScale = i;
				mUserListLayoutManager.setSpanCount(scale * i); //(mTweeters.size()+i-1)/i);
			}
			if (itemSizeScale != lastScale)
				notifyDataSetChanged();
		}

		@Override
		public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			return new ViewHolder(new ImageView(getActivity()));
		}

		@Override
		public long getItemId(int position) {
			return mTweeters.get(position).getTweeter().user_id;
		}

		@Override
		public void onBindViewHolder(ViewHolder holder, int position) {
			int itemSize = mViewSelectedUserList.getLayoutParams().height;
			if (mTweeters.size() > scale)
				itemSize = itemSize / itemSizeScale;
			holder.itemView.setLayoutParams(new GridLayoutManager.LayoutParams(itemSize, itemSize));
			if (mImageCache != null)
				mImageCache.assignImageView(((ImageView) holder.itemView), mTweeters.get(position).getTweeter().getProfileImageUrl(), null);
		}

		@Override
		public int getItemCount() {
			return mTweeters.size();
		}

		public class ViewHolder extends RecyclerView.ViewHolder {

			public ViewHolder(View itemView) {
				super(itemView);
			}
		}
	}

	private class AttachmentAdapter extends RecyclerView.Adapter<AttachmentAdapter.ViewHolder> implements TemplateTweetAttachment.AttachmentResolveResult {

		private final ArrayList<TemplateTweetAttachment> mAttachments = new ArrayList<>();
		private boolean mPreviousInsertBtnVisible;

		AttachmentAdapter() {
			setHasStableIds(true);
			mPreviousInsertBtnVisible = true;
		}

		@Override
		public long getItemId(int position) {
			if (position >= mAttachments.size())
				return -1;
			return mAttachments.get(position).mRuntimeId;
		}

		public boolean canAddMore() {
			if (mAttachments.size() >= 4)
				return mPreviousInsertBtnVisible = false;
			for (TemplateTweetAttachment a : mAttachments)
				if (!a.mIsImageMedia)
					return mPreviousInsertBtnVisible = false;
			return mPreviousInsertBtnVisible = true;
		}

		public void removeAll() {
			boolean wasAvailable = canAddMore();
			while (!mAttachments.isEmpty()) {
				TemplateTweetAttachment a = mAttachments.remove(0);
				if (a.mLocalPath != null) {
					File f = new File(a.mLocalPath);
					TemplateTweetAttachment.removeUnreferencedFile(f, a);
				}
				notifyItemRemoved(0);
			}
			if (!wasAvailable && canAddMore())
				notifyItemInserted(mAttachments.size());
			getNewTweet(TemplateTweet.getEditorTweet());
			TemplateTweet.saveEditorTweet();
		}

		public void removeItem(int index) {
			boolean wasAvailable = canAddMore();
			TemplateTweetAttachment a = mAttachments.remove(index);
			if (a.mLocalPath != null) {
				File f = new File(a.mLocalPath);
				TemplateTweetAttachment.removeUnreferencedFile(f, a);
			}
			notifyItemRemoved(index);
			if (!wasAvailable && canAddMore())
				notifyItemInserted(mAttachments.size());
			getNewTweet(TemplateTweet.getEditorTweet());
			TemplateTweet.saveEditorTweet();
		}

		public void insertItem(TemplateTweetAttachment a) {
			if (!canAddMore())
				return;
			mAttachments.add(a);
			notifyItemInserted(mAttachments.size() - 1);
			if (!canAddMore())
				notifyItemRemoved(mAttachments.size());
			getNewTweet(TemplateTweet.getEditorTweet());
			TemplateTweet.saveEditorTweet();
		}

		public void setData(List<TemplateTweetAttachment> list) {
			mAttachments.clear();
			for (TemplateTweetAttachment a : list) {
				if (a.mLocalPath == null && a.mUnresolvedUrl == null)
					continue;
				if (a.mLocalPath == null || !new File(a.mLocalPath).exists())
					a.resolve(getActivity(), this);
				mAttachments.add(a);
			}
			canAddMore();
			notifyDataSetChanged();
		}

		@Override
		public int getItemViewType(int position) {
			return R.layout.col_newtweet_attachment;
		}

		@Override
		public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false));
		}

		@Override
		public void onBindViewHolder(ViewHolder holder, int position) {
			holder.bindViewHolder(position);
		}

		@Override
		public int getItemCount() {
			return mAttachments.size() + (mPreviousInsertBtnVisible ? 1 : 0);
		}

		@Override
		public void onResolved(TemplateTweetAttachment attachment) {
			if (!mAttachments.contains(attachment))
				return;
			itemChanged(attachment);
			getNewTweet(TemplateTweet.getEditorTweet());
			TemplateTweet.saveEditorTweet();
			boolean isImageMedia = true;
			for (TemplateTweetAttachment a : mAttachments)
				isImageMedia &= a.mIsImageMedia;
			if (mAttachments.size() > 4 || (mAttachments.size() >= 2 && !isImageMedia))
				DarknovaApplication.showToast(R.string.new_tweet_attach_remove_required);
		}

		@Override
		public void onTypeResolved(TemplateTweetAttachment attachment) {
			itemChanged(attachment);
		}

		private void itemChanged(TemplateTweetAttachment attachment) {
			if (!mAttachments.contains(attachment))
				return;
			notifyItemChanged(mAttachments.indexOf(attachment));
			boolean mPrev = mPreviousInsertBtnVisible;
			boolean mNow = canAddMore();
			if (mPrev && !mNow)
				notifyItemRemoved(mAttachments.size());
			else if (!mPrev && mNow)
				notifyItemRemoved(mAttachments.size());
		}

		@Override
		public void onResolveFailed(TemplateTweetAttachment attachment, Exception e) {
			int index = mAttachments.indexOf(attachment);
			if (index == -1)
				return;
			removeItem(index);
		}

		public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
			ImageView mImageView;
			ImageView mTypeView;
			View mProgressView;

			public ViewHolder(View itemView) {
				super(itemView);
				itemView.setOnClickListener(this);
				mImageView = (ImageView) itemView.findViewById(R.id.image);
				mProgressView = itemView.findViewById(R.id.progress);
				mTypeView = (ImageView) itemView.findViewById(R.id.type);
			}

			public void bindViewHolder(int position) {
				if (position >= mAttachments.size()) {
					// add attachment button
					mImageView.setImageDrawable(ResTools.getDrawableByAttribute(getActivity(), R.attr.ic_editor_attach_file));
					mProgressView.setVisibility(View.GONE);
					mTypeView.setImageDrawable(null);
					return;
				}
				if (mImageCache != null) {
					TemplateTweetAttachment a = mAttachments.get(position);
					if (a.mTypeResolved || a.mUnresolvedUrl == null)
						mTypeView.setImageDrawable(ResTools.getDrawableByAttribute(getActivity(), mAttachments.get(position).mIsImageMedia ? R.attr.ic_image_image : R.attr.ic_av_videocam));
					else
						mTypeView.setImageDrawable(null);
					mImageCache.assignImageView(mImageView, mAttachments.get(position).mLocalPath, null);
					mImageCache.assignStatusIndicator(mProgressView, mAttachments.get(position).mLocalPath, null);
				}
			}

			@Override
			public void onClick(View v) {
				final int position = getAdapterPosition();
				if (position >= mAttachments.size()) {
					Intent newImageIntent = new Intent(Intent.ACTION_GET_CONTENT);
					newImageIntent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
					newImageIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
					newImageIntent.setType("image/*");
					startActivityForResult(newImageIntent, PICK_MEDIA);
					// upload
					return;
				}

				new AlertDialog.Builder(getActivity())
						.setMessage("remove?")
						.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								mAttachmentAdapter.removeItem(position);
							}
						}).setNegativeButton(android.R.string.no, null)
						.show();
			}
		}
	}
}
