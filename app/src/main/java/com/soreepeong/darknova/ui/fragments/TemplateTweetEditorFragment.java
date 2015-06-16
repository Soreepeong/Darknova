package com.soreepeong.darknova.ui.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.animation.RotateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.soreepeong.darknova.DarknovaApplication;
import com.soreepeong.darknova.R;
import com.soreepeong.darknova.core.ImageCache;
import com.soreepeong.darknova.services.TemplateTweetProvider;
import com.soreepeong.darknova.settings.TemplateTweet;
import com.soreepeong.darknova.settings.TemplateTweetAttachment;
import com.soreepeong.darknova.tools.FileTools;
import com.soreepeong.darknova.tools.ResTools;
import com.soreepeong.darknova.tools.StringTools;
import com.soreepeong.darknova.twitter.Tweet;
import com.soreepeong.darknova.twitter.Tweeter;
import com.soreepeong.darknova.twitter.TwitterEngine;
import com.soreepeong.darknova.ui.MultiContentFragmentActivity;
import com.soreepeong.darknova.ui.adapters.TemplateAdapter;

import org.apmem.tools.layouts.FlowLayout;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import pl.droidsonroids.gif.GifDrawable;

/**
 * New template tweet maker.
 *
 * @author Soreepeong
 */
public class TemplateTweetEditorFragment extends Fragment implements Tweeter.OnUserInformationChangedListener, ImageCache.OnImageCacheReadyListener, CompoundButton.OnCheckedChangeListener, View.OnClickListener, TwitterEngine.OnUserlistChangedListener, TextWatcher, AdapterView.OnItemSelectedListener, TemplateAdapter.OnTemplateTweetSelectedListener {

	public static final String PREF_LAST_WORKING_TEMPLATE_ID = "last_working_template_id";
	private static final int PICK_MEDIA = 1;
	private static final int REQUEST_EXTSDCARD_PERMISSION = 2;
	private static final String PREF_EXT_MEDIA_STORAGE = "external_media_storage";
	private static final Interpolator INTERPOLATOR = new AccelerateDecelerateInterpolator();
	private final HashMap<Tweeter, ToggleButton> mUserMaps = new HashMap<>();
	private SharedPreferences mDefaultPreference, mEditorPreference;
	private ViewGroup mViewTemplateTweetEditor;
	private Button mViewClearBtn;
	private ImageView mViewLocationBtn, mViewTemplateOptionBtn, mViewTemplateListBtn;
	private FrameLayout mViewUserSelectBtn;
	private TextView mViewUserSelectedText;
	private ImageView mViewUserSelectExpander;
	private ImageButton mViewWriteBtn;
	private EditText mViewEditor;
	private ImageCache mImageCache;
	private OnNewTweetVisibilityChangedListener mListener;
	private Tweet mInReplyTo;
	private FlowLayout mViewAccount;
	private View mViewAccountContainer, mViewEditorContainer, mViewLocationContainer, mViewOptionsContainer, mViewToolbarContainer;
	private RecyclerView mViewAttachList, mViewSelectedUserList, mViewTemplateList, mViewMediaList;
	private CheckBox mViewGeoUse, mViewGeoAutoresolve;
	private EditText mViewGeoLatitude, mViewGeoLongitude;
	private Button mViewGeoResolve, mViewGeoPick;
	private Spinner mViewTemplateType;
	private CheckBox mViewEnabled, mViewRemoveAfter, mViewUseRegEx;
	private EditText mViewInterval, mViewPattern, mViewTestPattern;
	private TextView mViewTimeStart, mViewTimeEnd;

	private boolean mIsShown, mIsActionbarShown = true;
	private AttachmentAdapter mAttachmentAdapter;
	private LinearLayoutManager mAttachmentLayoutManager;
	private UserImageAdapter mUserAdapter;
	private GridLayoutManager mUserListLayoutManager;
	private TemplateAdapter mTemplateAdapter;
	private LinearLayoutManager mTemplateLayoutManager;
	private MediaAdapter mMediaAdapter;
	private GridLayoutManager mMediaListLayoutManager;

	private TemplateTweet mTemplateTweet;

	private File mCurrentPhotoPath;

	private ViewStub mViewStub;

	private boolean mNoReloadLastWorking;

	private File createImageFile() {
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(new Date());
		String imageFileName = "JPEG_" + timeStamp + "_";
		File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
		mCurrentPhotoPath = new File(storageDir + "/" + imageFileName + ".jpg");
		return mCurrentPhotoPath;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mImageCache = ImageCache.getCache(getActivity(), this);
		mDefaultPreference = PreferenceManager.getDefaultSharedPreferences(getActivity());
		mEditorPreference = getActivity().getSharedPreferences("templatetweeteditorfragment", Context.MODE_MULTI_PROCESS);
		mViewTemplateTweetEditor = (ViewGroup) inflater.inflate(R.layout.fragment_templatetweet, container, false);
		mViewWriteBtn = (ImageButton) mViewTemplateTweetEditor.findViewById(R.id.write_btn);
		mViewClearBtn = (Button) mViewTemplateTweetEditor.findViewById(R.id.clear_btn);
		mViewUserSelectBtn = (FrameLayout) mViewTemplateTweetEditor.findViewById(R.id.user_select);
		mViewUserSelectExpander = (ImageView) mViewTemplateTweetEditor.findViewById(R.id.user_select_expander);
		mViewUserSelectedText = (TextView) mViewTemplateTweetEditor.findViewById(R.id.user_select_text);
		mViewSelectedUserList = (RecyclerView) mViewTemplateTweetEditor.findViewById(R.id.selected_user_image_list);

		mViewTemplateOptionBtn = (ImageView) mViewTemplateTweetEditor.findViewById(R.id.options);
		mViewTemplateListBtn = (ImageView) mViewTemplateTweetEditor.findViewById(R.id.template_list);
		mViewLocationBtn = (ImageView) mViewTemplateTweetEditor.findViewById(R.id.location);
		mViewAttachList = (RecyclerView) mViewTemplateTweetEditor.findViewById(R.id.attach_list);
		mViewEditor = (EditText) mViewTemplateTweetEditor.findViewById(R.id.editor);

		mViewEditorContainer = mViewTemplateTweetEditor.findViewById(R.id.editor_container);
		mViewToolbarContainer = mViewTemplateTweetEditor.findViewById(R.id.toolbar);

		mViewStub = (ViewStub) mViewTemplateTweetEditor.findViewById(R.id.stub_extra);

		mViewWriteBtn.setOnClickListener(this);
		mViewClearBtn.setOnClickListener(this);
		mViewUserSelectBtn.setOnClickListener(this);
		mViewTemplateOptionBtn.setOnClickListener(this);
		mViewTemplateListBtn.setOnClickListener(this);
		mViewLocationBtn.setOnClickListener(this);

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

		if (savedInstanceState != null && savedInstanceState.getBoolean("open"))
			showNewTweet();

		mViewTemplateTweetEditor.setVisibility(View.GONE);
		return mViewTemplateTweetEditor;
	}

	private void loadExtra() {
		if (mViewStub == null)
			return;
		if (mTemplateTweet != null)
			saveTemplateTweet();
		ViewGroup v = (ViewGroup) mViewStub.inflate();
		mViewStub = null;
		mViewTemplateList = (RecyclerView) v.findViewById(R.id.list_templates);
		mViewLocationContainer = v.findViewById(R.id.template_location);
		mViewOptionsContainer = v.findViewById(R.id.template_options);
		mViewAccount = (FlowLayout) v.findViewById(R.id.account_list);
		mViewAccountContainer = v.findViewById(R.id.account_list_container);
		mViewGeoUse = (CheckBox) v.findViewById(R.id.use_geo);
		mViewGeoAutoresolve = (CheckBox) v.findViewById(R.id.autoresolve);
		mViewGeoLatitude = (EditText) v.findViewById(R.id.latitude);
		mViewGeoLongitude = (EditText) v.findViewById(R.id.longitude);
		mViewGeoResolve = (Button) v.findViewById(R.id.resolve);
		mViewGeoPick = (Button) v.findViewById(R.id.pick);
		mViewTemplateType = (Spinner) v.findViewById(R.id.template_type);
		mViewEnabled = (CheckBox) v.findViewById(R.id.enabled);
		mViewRemoveAfter = (CheckBox) v.findViewById(R.id.remove_after);
		mViewInterval = (EditText) v.findViewById(R.id.interval);
		mViewTimeStart = (TextView) v.findViewById(R.id.from);
		mViewTimeEnd = (TextView) v.findViewById(R.id.to);
		mViewUseRegEx = (CheckBox) v.findViewById(R.id.use_regex);
		mViewPattern = (EditText) v.findViewById(R.id.pattern);
		mViewTestPattern = (EditText) v.findViewById(R.id.test);
		mViewMediaList = (RecyclerView) v.findViewById(R.id.media_list);


		ArrayList<View> views = new ArrayList<>();
		for (int i = 0, i_ = v.getChildCount(); i < i_; i++)
			views.add(v.getChildAt(i));
		v.removeAllViews();
		int basePosition = mViewTemplateTweetEditor.indexOfChild(v);
		mViewTemplateTweetEditor.removeViewAt(basePosition);
		for (int i = 0; i < views.size(); i++) {
			mViewTemplateTweetEditor.addView(views.get(i), basePosition + i);
		}


		mViewGeoResolve.setOnClickListener(this);
		mViewGeoPick.setOnClickListener(this);
		mViewGeoUse.setOnCheckedChangeListener(this);
		mViewTimeStart.setOnClickListener(this);
		mViewTimeEnd.setOnClickListener(this);
		mViewTemplateType.setOnItemSelectedListener(this);

		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getActivity(), R.array.new_tweet_type_array, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mViewTemplateType.setAdapter(adapter);
		mViewTemplateList.setLayoutManager(mTemplateLayoutManager = new LinearLayoutManager(getActivity()));
		mViewMediaList.setLayoutManager(mMediaListLayoutManager = new GridLayoutManager(getActivity(), 2, GridLayoutManager.VERTICAL, true));
		mMediaListLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
			@Override
			public int getSpanSize(int position) {
				return position == 0 ? 2 : 1;
			}
		});
		if (mTemplateTweet == null)
			loadTemplateTweet(mDefaultPreference.getLong(PREF_LAST_WORKING_TEMPLATE_ID, -1));
		else {
			mTemplateTweet.updateSelf(getActivity().getContentResolver());
			loadTemplateTweet(mTemplateTweet.id);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (!mNoReloadLastWorking)
			loadTemplateTweet(mDefaultPreference.getLong(PREF_LAST_WORKING_TEMPLATE_ID, -1));
	}

	private void loadTemplateTweet(long id) {
		Cursor c = getActivity().getContentResolver().query(TemplateTweetProvider.URI_TEMPLATES, null, "_id=?", new String[]{Long.toString(id)}, null);
		if (c.moveToFirst())
			mTemplateTweet = new TemplateTweet(c, getActivity().getContentResolver());
		else {
			mTemplateTweet = new TemplateTweet(getActivity().getContentResolver(), mTemplateTweet == null ? null : mTemplateTweet.mUserIdList);
			id = mTemplateTweet.id;
		}
		c.close();
		SharedPreferences.Editor editor = mDefaultPreference.edit();
		editor.putLong(PREF_LAST_WORKING_TEMPLATE_ID, id);
		editor.apply();

		mInReplyTo = Tweet.getTweet(mTemplateTweet.in_reply_to_id);
		if (mTemplateTweet.text != null) {
			mViewEditor.setText(mTemplateTweet.text);
			mViewEditor.setSelection(mTemplateTweet.selection_start = Math.min(mTemplateTweet.selection_start, mTemplateTweet.text.length()),
					mTemplateTweet.selection_end = Math.min(mTemplateTweet.selection_end, mTemplateTweet.text.length()));
		} else
			mViewEditor.setText("");

		applyUserText();
		for (TemplateTweetAttachment attachment : mTemplateTweet.mAttachments)
			if (!attachment.mLocalFileExists || attachment.media_type == 0)
				attachment.resolve(getActivity(), mAttachmentAdapter);
		mAttachmentAdapter.notifyDataSetChanged();

		if (mViewStub == null) {
			refillUserMaps(TwitterEngine.getAll());
			mViewGeoUse.setChecked(mTemplateTweet.use_coordinates);
			mViewGeoAutoresolve.setChecked(mTemplateTweet.autoresolve_coordinates);
			mViewEnabled.setChecked(mTemplateTweet.enabled);
			mViewRemoveAfter.setChecked(mTemplateTweet.remove_after);
			mViewUseRegEx.setChecked(mTemplateTweet.trigger_use_regex);
			mViewPattern.setText(mTemplateTweet.trigger_pattern);
			mViewGeoLatitude.setText(Float.toString(mTemplateTweet.latitude));
			mViewGeoLongitude.setText(Float.toString(mTemplateTweet.longitude));
			assignTimeValue(mViewTimeStart, mTemplateTweet.time_start);
			assignTimeValue(mViewTimeEnd, mTemplateTweet.time_end);
			applyTweetType();
			if (mTemplateAdapter != null) {
				mTemplateAdapter.setExcludedId(mTemplateTweet.id);
			}
		}

	}

	private void saveTemplateTweet() {
		if (mTemplateTweet == null)
			return;
		mTemplateTweet.in_reply_to_id = mInReplyTo == null ? 0 : mInReplyTo.id;
		mTemplateTweet.text = mViewEditor.getText().toString();
		if (mViewStub == null) {
			mTemplateTweet.mUserIdList.clear();
			for (Tweeter ttr : mUserMaps.keySet())
				if (mUserMaps.get(ttr).isChecked())
					mTemplateTweet.mUserIdList.add(ttr.user_id);
		}
		if (mViewStub == null) {
			mTemplateTweet.selection_start = mViewEditor.getSelectionStart();
			mTemplateTweet.selection_end = mViewEditor.getSelectionEnd();
			mTemplateTweet.use_coordinates = mViewGeoUse.isChecked();
			mTemplateTweet.autoresolve_coordinates = mViewGeoAutoresolve.isChecked();
			mTemplateTweet.remove_after = mViewRemoveAfter.isChecked();
			mTemplateTweet.trigger_pattern = mViewPattern.getText().toString();
			mTemplateTweet.trigger_use_regex = mViewUseRegEx.isChecked();
			mTemplateTweet.time_start = (long) mViewTimeStart.getTag(R.id.UNIX_TIMESTAMP);
			mTemplateTweet.time_end = (long) mViewTimeEnd.getTag(R.id.UNIX_TIMESTAMP);
		}
		mTemplateTweet.updateSelf(getActivity().getContentResolver());
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		for (Tweeter t : mUserMaps.keySet()) {
			t.removeOnChangeListener(this);
		}
		if (mTemplateAdapter != null) {
			mTemplateAdapter.done();
			mTemplateAdapter = null;
		}
		if (mMediaAdapter != null) {
			mMediaAdapter.done();
			mMediaAdapter = null;
		}
		TwitterEngine.removeOnUserlistChangedListener(this);
		mImageCache = null;
	}

	private void refillUserMaps(ArrayList<TwitterEngine> engines) {
		if (mViewStub != null)
			return;
		for (Tweeter t : mUserMaps.keySet())
			t.removeOnChangeListener(this);
		mUserMaps.clear();
		mViewAccount.removeAllViews();
		for (TwitterEngine engine : engines) {
			Tweeter t = engine.getTweeter();
			ToggleButton box = (ToggleButton) getActivity().getLayoutInflater().inflate(R.layout.col_templatetweet_user, mViewAccount, false);
			box.setChecked(mTemplateTweet.mUserIdList.contains(t.user_id));
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
			if (mViewTemplateTweetEditor.getVisibility() == View.VISIBLE) return;
			mViewTemplateTweetEditor.setVisibility(View.VISIBLE);
			mViewTemplateTweetEditor.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.show_upward));
			mViewEditor.requestFocus();
		} else
			mListener.onNewTweetVisibilityChanged(false);
	}

	public void hideWithActionBar() {
		mIsActionbarShown = false;
		if (mListener != null)
			mListener.onNewTweetVisibilityChanged(true);
		if (mViewTemplateTweetEditor.getVisibility() != View.VISIBLE) return;
		ResTools.hideWithAnimation(mViewTemplateTweetEditor, R.anim.hide_downward, true);
	}

	public void initializeNewTweet() {
		mIsShown = true;
		if (!mIsActionbarShown)
			return;
		mNoReloadLastWorking = true;
		if (mViewTemplateTweetEditor.getVisibility() == View.VISIBLE) return;
		loadTemplateTweet(mDefaultPreference.getLong(PREF_LAST_WORKING_TEMPLATE_ID, -1));
		mViewTemplateTweetEditor.setVisibility(View.VISIBLE);
		if (mListener != null)
			mListener.onNewTweetVisibilityChanged(true);
		showKeyboard();
		mViewSelectedUserList.post(new Runnable() {
			@Override
			public void run() {
				mUserAdapter.invalidate();
			}
		});
	}

	public void showNewTweet() {
		mIsShown = true;
		if (!mIsActionbarShown)
			return;
		if (mViewTemplateTweetEditor.getVisibility() == View.VISIBLE) return;

		mViewTemplateTweetEditor.setVisibility(View.VISIBLE);

		mViewTemplateTweetEditor.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.show_upward));
		if (mListener != null)
			mListener.onNewTweetVisibilityChanged(true);

		showKeyboard();

		mViewSelectedUserList.post(new Runnable() {
			@Override
			public void run() {
				mUserAdapter.invalidate();
			}
		});
	}

	private void showKeyboard() {
		mViewEditor.requestFocus();
		InputMethodManager mgr = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
		mgr.showSoftInput(mViewEditor, InputMethodManager.SHOW_IMPLICIT);
		mgr.showSoftInput(mViewEditor, 0);
	}

	private void hideKeyboard() {
		InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(getActivity().getWindow().getDecorView().getWindowToken(), 0);
	}

	public void hideNewTweet() {
		mIsShown = false;
		if (mViewTemplateTweetEditor.getVisibility() != View.VISIBLE) return;
		ResTools.hideWithAnimation(mViewTemplateTweetEditor, R.anim.hide_downward, true);
		if (mListener != null)
			mListener.onNewTweetVisibilityChanged(false);

		hideKeyboard();

		switchViews(null);

		mTemplateTweet.updateSelf(getActivity().getContentResolver());
	}

	public boolean isNewTweetVisible() {
		return mIsShown && mIsActionbarShown;
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
		if (mTemplateTweet == null)
			loadTemplateTweet(mDefaultPreference.getLong(PREF_LAST_WORKING_TEMPLATE_ID, -1));
		for (TwitterEngine e : TwitterEngine.getAll())
			if (mTemplateTweet.mUserIdList.contains(e.getUserId()))
				postFrom.add(e);
		return postFrom;
	}

	@Override
	public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
		if (parent.equals(mViewTemplateType)) {
			switch (position) {
				case 0:
					mTemplateTweet.type = TemplateTweetProvider.TEMPLATE_TYPE_ONESHOT;
					break;
				case 1:
					mTemplateTweet.type = TemplateTweetProvider.TEMPLATE_TYPE_SCHEDULED;
					break;
				case 2:
					mTemplateTweet.type = TemplateTweetProvider.TEMPLATE_TYPE_PERIODIC;
					break;
				case 3:
					mTemplateTweet.type = TemplateTweetProvider.TEMPLATE_TYPE_REPLY;
					break;
			}
			applyTweetType();
		}
	}

	private void applyTweetType() {
		if (mViewStub != null)
			return;
		mViewEnabled.setChecked(false);
		switch (mTemplateTweet.type) {
			case TemplateTweetProvider.TEMPLATE_TYPE_ONESHOT:
				mViewEnabled.setVisibility(View.GONE);
				mViewUseRegEx.setVisibility(View.GONE);
				mViewInterval.setVisibility(View.GONE);
				mViewTimeStart.setVisibility(View.GONE);
				mViewTimeEnd.setVisibility(View.GONE);
				mViewPattern.setVisibility(View.GONE);
				mViewTestPattern.setVisibility(View.GONE);
				break;
			case TemplateTweetProvider.TEMPLATE_TYPE_SCHEDULED:
				mViewEnabled.setVisibility(View.VISIBLE);
				mViewUseRegEx.setVisibility(View.GONE);
				mViewInterval.setVisibility(View.GONE);
				mViewTimeStart.setVisibility(View.VISIBLE);
				mViewTimeEnd.setVisibility(View.GONE);
				mViewPattern.setVisibility(View.GONE);
				mViewTestPattern.setVisibility(View.GONE);
				break;
			case TemplateTweetProvider.TEMPLATE_TYPE_PERIODIC:
				mViewEnabled.setVisibility(View.VISIBLE);
				mViewUseRegEx.setVisibility(View.GONE);
				mViewInterval.setVisibility(View.VISIBLE);
				mViewTimeStart.setVisibility(View.VISIBLE);
				mViewTimeEnd.setVisibility(View.VISIBLE);
				mViewPattern.setVisibility(View.GONE);
				mViewTestPattern.setVisibility(View.GONE);
				break;
			case TemplateTweetProvider.TEMPLATE_TYPE_REPLY:
				mViewEnabled.setVisibility(View.VISIBLE);
				mViewUseRegEx.setVisibility(View.VISIBLE);
				mViewInterval.setVisibility(View.GONE);
				mViewTimeStart.setVisibility(View.VISIBLE);
				mViewTimeEnd.setVisibility(View.VISIBLE);
				mViewPattern.setVisibility(View.VISIBLE);
				mViewTestPattern.setVisibility(View.VISIBLE);
				break;
		}
		ViewGroup cont = ((ViewGroup) mViewEnabled.getParent());
		for (int i = 1, i_ = cont.getChildCount(); i < i_; i++) {
			if (cont.getChildAt(i).equals(mViewInterval) ||
					cont.getChildAt(i).equals(mViewTimeStart) ||
					cont.getChildAt(i).equals(mViewTimeEnd) ||
					cont.getChildAt(i).equals(mViewUseRegEx) ||
					cont.getChildAt(i).equals(mViewTestPattern))
				cont.getChildAt(i - 1).setVisibility(cont.getChildAt(i).getVisibility());
		}
	}

	@Override
	public void onCheckedChanged(CompoundButton v, boolean isChecked) {
		if (mUserMaps.containsValue(v)) {
			v.getCompoundDrawables()[0].setAlpha(isChecked ? 255 : 80);
			saveTemplateTweet();
			applyUserText();
		} else if (v.equals(mViewGeoUse)) {
			mViewLocationBtn.setImageDrawable(ResTools.getDrawableByAttribute(getActivity(), mViewLocationContainer.getVisibility() == View.VISIBLE ? R.attr.ic_navigation_expand_less : (mViewGeoUse.isChecked() ? R.attr.ic_communication_location_on : R.attr.ic_communication_location_off)));
		}
	}

	private boolean isEmpty() {
		return mTemplateTweet == null || (mViewEditor.getText().length() == 0 && mTemplateTweet.mAttachments.isEmpty());
	}

	public void setContent(String text) {
		if (!isEmpty()) {
			saveTemplateTweet();
			loadTemplateTweet(-1);
		}
		mViewEditor.setText(mTemplateTweet.text = text);
	}

	@Override
	public void onClick(View v) {
		if (v.equals(mViewWriteBtn)) {
			if (isEmpty())
				hideNewTweet();
			else {
				if (mTemplateTweet.type == TemplateTweetProvider.TEMPLATE_TYPE_ONESHOT)
					mTemplateTweet.enabled = true;
				else
					mTemplateTweet.enabled = mViewEnabled.isChecked();
				saveTemplateTweet();
				loadTemplateTweet(-1);
			}
		} else if (v.equals(mViewClearBtn)) {
			if (isEmpty())
				hideNewTweet();
			else {
				saveTemplateTweet();
				loadTemplateTweet(-1);
			}
		} else if (v.equals(mViewUserSelectBtn)) {
			loadExtra();
			switchViews(mViewAccountContainer);
		} else if (v.equals(mViewTemplateOptionBtn)) {
			loadExtra();
			switchViews(mViewOptionsContainer);
		} else if (v.equals(mViewTemplateListBtn)) {
			loadExtra();
			switchViews(mViewTemplateList);
		} else if (v.equals(mViewLocationBtn)) {
			loadExtra();
			switchViews(mViewLocationContainer);
		} else if (v.equals(mViewTimeStart)) {
			datetimepicker(mViewTimeStart, R.string.new_tweet_time_start);
		} else if (v.equals(mViewTimeEnd)) {
			datetimepicker(mViewTimeEnd, R.string.new_tweet_time_end);
		}
	}

	private void assignTimeValue(TextView text, long time) {
		text.setText(DateFormat.getDateFormat(getActivity()).format(new Date(time)) + " " + DateFormat.getTimeFormat(getActivity()).format(new Date(time)));
		text.setTag(R.id.UNIX_TIMESTAMP, time);
	}

	private void datetimepicker(final TextView initiator, @StringRes int titleResId) {
		final View dialogContent = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_datetimepicker, (ViewGroup) getView(), false);
		final DatePicker date = (DatePicker) dialogContent.findViewById(R.id.datepicker);
		final TimePicker time = (TimePicker) dialogContent.findViewById(R.id.timepicker);
		final Calendar c = Calendar.getInstance();
		if (initiator.getTag(R.id.UNIX_TIMESTAMP) != null)
			c.setTimeInMillis((long) initiator.getTag(R.id.UNIX_TIMESTAMP));
		date.init(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH), null);
		time.setCurrentHour(c.get(Calendar.HOUR_OF_DAY));
		time.setCurrentMinute(c.get(Calendar.MINUTE));
		new AlertDialog.Builder(getActivity())
				.setView(dialogContent)
				.setTitle(titleResId)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						c.set(date.getYear(), date.getMonth(), date.getDayOfMonth(), time.getCurrentHour(), time.getCurrentMinute());
						assignTimeValue(initiator, c.getTimeInMillis());
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	public boolean switchViews(View v) {
		android.util.Log.d("TemplateTweetEditor", "switchViews called");
		boolean somethingHidden = false;
		if (mViewStub == null) {
			if (mViewMediaList.getVisibility() == View.VISIBLE) {
				mViewMediaList.setVisibility(View.GONE);
				mViewEditor.setVisibility(View.VISIBLE);
				mViewToolbarContainer.setVisibility(View.VISIBLE);
				mMediaListLayoutManager.removeAllViews();
				somethingHidden = true;
			} else if (v == mViewMediaList) {
				mViewMediaList.setVisibility(View.VISIBLE);
				mViewEditor.setVisibility(View.GONE);
				mViewToolbarContainer.setVisibility(View.GONE);
			}
			if (v == mViewAccountContainer || mViewAccountContainer.getVisibility() == View.VISIBLE) {
				mViewUserSelectExpander.setImageDrawable(ResTools.getDrawableByAttribute(getActivity(), mViewAccountContainer.getVisibility() != View.VISIBLE ? R.attr.ic_navigation_expand_less : R.attr.ic_navigation_expand_more));
				RotateAnimation ani = new RotateAnimation(-180, 0, RotateAnimation.RELATIVE_TO_SELF, 0.5f, RotateAnimation.RELATIVE_TO_SELF, 0.5f);
				ani.setDuration(300);
				ani.setInterpolator(INTERPOLATOR);
				mViewUserSelectExpander.startAnimation(ani);
			}
			for (View v_ : new View[]{mViewAccountContainer, mViewOptionsContainer, mViewTemplateList, mViewLocationContainer}) {
				if (v_ == v) {
					if (v.getVisibility() == View.VISIBLE) {
						v.setVisibility(View.GONE);
						mViewEditorContainer.setVisibility(View.VISIBLE);
						mViewEditor.requestFocus();
						somethingHidden = true;
					} else {
						v.setVisibility(View.VISIBLE);
						mViewEditorContainer.setVisibility(View.GONE);
					}
				} else {
					if (v_.getVisibility() != View.VISIBLE)
						continue;
					v_.setVisibility(View.GONE);
					somethingHidden = true;
				}
			}
			if (mViewMediaList.getVisibility() == View.VISIBLE && mMediaAdapter == null) {
				mViewMediaList.setAdapter(mMediaAdapter = new MediaAdapter());
			} else if (mViewTemplateList.getVisibility() == View.VISIBLE && mTemplateAdapter == null) {
				mViewTemplateList.setAdapter(mTemplateAdapter = new TemplateAdapter(getActivity(), this));
				mTemplateAdapter.setExcludedId(mTemplateTweet.id);
			}
		}
		if (v == null) {
			mViewEditorContainer.setVisibility(View.VISIBLE);
		}
		mViewTemplateOptionBtn.setImageDrawable(ResTools.getDrawableByAttribute(getActivity(), mViewStub == null && mViewOptionsContainer.getVisibility() == View.VISIBLE ? R.attr.ic_navigation_expand_less : R.attr.ic_action_settings));
		mViewTemplateListBtn.setImageDrawable(ResTools.getDrawableByAttribute(getActivity(), mViewStub == null && mViewTemplateList.getVisibility() == View.VISIBLE ? R.attr.ic_navigation_expand_less : R.attr.ic_content_drafts));
		mViewLocationBtn.setImageDrawable(ResTools.getDrawableByAttribute(getActivity(), mViewStub == null && mViewLocationContainer.getVisibility() == View.VISIBLE ? R.attr.ic_navigation_expand_less : (mViewGeoUse != null && mViewGeoUse.isChecked() ? R.attr.ic_communication_location_on : R.attr.ic_communication_location_off)));
		if (mViewEditorContainer.getVisibility() == View.VISIBLE && mViewEditor.getVisibility() == View.VISIBLE && isNewTweetVisible())
			showKeyboard();
		else
			hideKeyboard();
		if (isNewTweetVisible() && ((mViewTemplateList != null && mViewTemplateList.getVisibility() == View.VISIBLE) || (mViewMediaList != null && mViewMediaList.getVisibility() == View.VISIBLE)))
			((MultiContentFragmentActivity) getActivity()).hideContents();
		else
			((MultiContentFragmentActivity) getActivity()).showContents();
		return somethingHidden;
	}

	private void applyUserText() {
		ArrayList<TwitterEngine> postFrom = getPostFromList();
		Collections.sort(postFrom);
		mViewUserSelectedText.setVisibility(postFrom.size() <= 1 ? View.VISIBLE : View.GONE);
		mViewUserSelectedText.setText(postFrom.size() == 0 ? getString(R.string.new_tweet_select_user) : postFrom.get(0).getScreenName());
		if (postFrom.isEmpty())
			mViewUserSelectedText.setPadding(0, 0, 0, 0);
		else if (ViewCompat.getLayoutDirection(mViewUserSelectedText) == ViewCompat.LAYOUT_DIRECTION_LTR)
			mViewUserSelectedText.setPadding(mViewUserSelectedText.getLayoutParams().height, 0, 0, 0);
		else
			mViewUserSelectedText.setPadding(0, 0, mViewUserSelectedText.getLayoutParams().height, 0);
		mUserAdapter.updateList(postFrom);
	}

	@Override
	public void onPause() {
		super.onPause();
		saveTemplateTweet();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean("open", mIsShown);
	}

	@Override
	public void onUserlistChanged(List<TwitterEngine> engines, List<TwitterEngine> oldEngines) {
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
			mViewClearBtn.setText(R.string.new_tweet_hide);
		else
			mViewClearBtn.setText(s.toString().trim().length() + "/" + 140);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == PICK_MEDIA && resultCode == Activity.RESULT_OK) {
			try {
				TemplateTweetAttachment a = new TemplateTweetAttachment(mCurrentPhotoPath == null ? data.getData() : Uri.fromFile(mCurrentPhotoPath), getActivity().getContentResolver(), mTemplateTweet);
				mAttachmentAdapter.insertItem(a);
				a.resolve(getActivity(), mAttachmentAdapter);
				if (mCurrentPhotoPath != null) {
					Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
					Uri contentUri = Uri.fromFile(mCurrentPhotoPath);
					mediaScanIntent.setData(contentUri);
					getActivity().sendBroadcast(mediaScanIntent);
				}
			} catch (Exception e) {
				Toast.makeText(getActivity(), R.string.new_tweet_attach_invalid, Toast.LENGTH_LONG).show();
			}
			switchViews(null);
		} else if (requestCode == REQUEST_EXTSDCARD_PERMISSION && resultCode == Activity.RESULT_OK && Build.VERSION.SDK_INT >= 19) {
			Uri u = data.getData();
			android.util.Log.d("Darknova", u.toString());
			getActivity().getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
			mEditorPreference.edit().putString(PREF_EXT_MEDIA_STORAGE, u.toString()).apply();
		}
		mCurrentPhotoPath = null;
	}

	@Override
	public void onNothingSelected(AdapterView<?> parent) {

	}

	@Override
	public void onTemplateTweetSelected(TemplateAdapter adapter, TemplateTweet t) {
		if (isEmpty())
			mTemplateTweet.removeSelf(getActivity().getContentResolver());
		else
			saveTemplateTweet();
		loadTemplateTweet(t.id);
		switchViews(null);
		mViewEditor.requestFocus();
	}

	public interface OnNewTweetVisibilityChangedListener {
		void onNewTweetVisibilityChanged(boolean visible);
	}

	private class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.ViewHolder> {
		private final ContentResolver mResolver;
		private final String[] projection;
		private final ArrayList<Long> mNoIds = new ArrayList<>();
		private Cursor mCursor;
		private int filterType;
		private final ContentObserver mObserver = new ContentObserver(new Handler()) {
			@Override
			public void onChange(boolean selfChange) {
				refill();
			}
		};

		public MediaAdapter() {
			if (Build.VERSION.SDK_INT >= 16)
				projection = new String[]{
						MediaStore.MediaColumns._ID,
						MediaStore.MediaColumns.DATE_MODIFIED,
						MediaStore.MediaColumns.DATA,
						MediaStore.MediaColumns.MIME_TYPE,
						MediaStore.MediaColumns.SIZE,
						MediaStore.MediaColumns.WIDTH,
						MediaStore.MediaColumns.HEIGHT,
						MediaStore.Video.VideoColumns.DURATION
				};
			else
				projection = new String[]{
						MediaStore.MediaColumns._ID,
						MediaStore.MediaColumns.DATE_MODIFIED,
						MediaStore.MediaColumns.DATA,
						MediaStore.MediaColumns.MIME_TYPE,
						MediaStore.MediaColumns.SIZE,
						MediaStore.Video.VideoColumns.DURATION
				};
			setHasStableIds(true);
			mResolver = getActivity().getContentResolver();
			mResolver.registerContentObserver(MediaStore.Files.getContentUri("external"), true, mObserver);
			refill();
		}

		public void done() {
			mCursor.close();
		}

		public void refill() {
			if (mCursor != null)
				mCursor.close();
			String whereImage = "(" + MediaStore.MediaColumns.MIME_TYPE + " LIKE 'image/%' AND " + MediaStore.MediaColumns.MIME_TYPE + "<>'image/gif')";
			String whereGif = "(" + MediaStore.MediaColumns.MIME_TYPE + "='image/gif' AND " + MediaStore.MediaColumns.SIZE + "<5242880)";
			String whereVideo = "(" + MediaStore.MediaColumns.MIME_TYPE + "='video/mp4' AND "
					+ MediaStore.MediaColumns.SIZE + "<=15728640 AND "
					+ MediaStore.Video.VideoColumns.DURATION + "<=30000)";
			String where;
			switch (filterType) {
				case TemplateTweetProvider.MEDIA_TYPE_IMAGE:
					where = whereImage;
					break;
				case TemplateTweetProvider.MEDIA_TYPE_GIF:
					where = whereGif;
					break;
				case TemplateTweetProvider.MEDIA_TYPE_VIDEO:
					where = whereVideo;
					break;
				default:
					where = "(" + whereImage + " OR " + whereGif + " OR " + whereVideo + ")";
			}
			if (!mNoIds.isEmpty()) {
				for (long l : mNoIds)
					where += " AND " + MediaStore.MediaColumns._ID + "<>" + l;
			}
			mCursor = mResolver.query(MediaStore.Files.getContentUri("external"), projection, where, null, MediaStore.MediaColumns.DATE_MODIFIED + " DESC");
			notifyDataSetChanged();
		}

		@Override
		public long getItemId(int position) {
			if (position == 0) {
				return -1;
			}
			position--;
			mCursor.moveToPosition(position);
			return mCursor.getInt(mCursor.getColumnIndex(MediaStore.MediaColumns._ID));
		}

		@Override
		public int getItemViewType(int position) {
			if (position == 0)
				return R.layout.col_templatetweet_attach_external;
			else
				return R.layout.col_templatetweet_attach_item;
		}

		@Override
		public MediaAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, final int viewType) {
			FrameLayout f = new FrameLayout(parent.getContext()) {
				@Override
				protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
					if (viewType == R.layout.col_templatetweet_attach_external)
						super.onMeasure(widthMeasureSpec, getChildAt(0).getLayoutParams().height + MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec) / 4, MeasureSpec.EXACTLY));
					else
						super.onMeasure(widthMeasureSpec, widthMeasureSpec);
				}
			};
			f.addView(LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false));
			return new ViewHolder(f);
		}

		@Override
		public void onBindViewHolder(MediaAdapter.ViewHolder holder, int position) {
			if (position != 0)
				holder.bindViewHolder(position - 1);
		}

		@Override
		public int getItemCount() {
			return mCursor.getCount() + 1;
		}

		public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, AdapterView.OnItemSelectedListener, View.OnLongClickListener {
			ImageView mImageView;
			ImageView mTypeView;
			View mProgressView;
			View mPickImage, mPickVideo, mTakeImage, mTakeVideo;
			Spinner mFilterType;
			TextView mDetails;

			public ViewHolder(View itemView) {
				super(itemView);
				itemView.setOnClickListener(this);
				itemView.setOnLongClickListener(this);
				mImageView = (ImageView) itemView.findViewById(R.id.image);
				mProgressView = itemView.findViewById(R.id.progress);
				mTypeView = (ImageView) itemView.findViewById(R.id.type);
				mDetails = (TextView) itemView.findViewById(R.id.details);
				mPickImage = itemView.findViewById(R.id.pick_image);
				if (mPickImage != null) {
					mFilterType = (Spinner) itemView.findViewById(R.id.attachment_type);
					ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getActivity(), R.array.new_tweet_attachment_type_array, android.R.layout.simple_spinner_item);
					adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
					mFilterType.setAdapter(adapter);
					mPickVideo = itemView.findViewById(R.id.pick_video);
					mTakeImage = itemView.findViewById(R.id.take_image);
					mTakeVideo = itemView.findViewById(R.id.take_video);
					mPickImage.setOnClickListener(this);
					mPickVideo.setOnClickListener(this);
					mTakeImage.setOnClickListener(this);
					mTakeVideo.setOnClickListener(this);
					mFilterType.setOnItemSelectedListener(this);
				}
			}

			public void bindViewHolder(int position) {
				mCursor.moveToPosition(position);
				final String mimeType = mCursor.getString(3);
				final long id = mCursor.getLong(0);
				String format;
				if (mimeType.startsWith("video/")) {
					mTypeView.setImageDrawable(ResTools.getDrawableByAttribute(getActivity(), R.attr.ic_av_videocam));
					format = " (" + StringTools.fileSize(mCursor.getLong(4)) + ")\n" + DateUtils.formatElapsedTime(mCursor.getLong(mCursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)) / 1000) + "\n";
				} else if (mimeType.equalsIgnoreCase("image/gif")) {
					mTypeView.setImageDrawable(ResTools.getDrawableByAttribute(getActivity(), R.attr.ic_av_videocam));
					format = " (" + StringTools.fileSize(mCursor.getLong(4)) + ")\n";
				} else {
					mTypeView.setImageDrawable(ResTools.getDrawableByAttribute(getActivity(), R.attr.ic_image_image));
					format = "\n";
				}
				final String fileName = new File(mCursor.getString(2)).getName();
				final String finalFormat;
				if (Build.VERSION.SDK_INT >= 16 && mCursor.getString(5) != null) {
					mDetails.setText(mCursor.getString(5) + "x" + mCursor.getString(6) + format + fileName);
					if (mimeType.startsWith("video/") && (mCursor.getInt(5) >= 1280 || mCursor.getInt(6) >= 1024))
						mDetails.setTextColor(0xFFFF0000);
					else
						mDetails.setTextColor(0xFFFFFFFF);
					finalFormat = mimeType.equals("image/gif") ? format : null;
				} else {
					mDetails.setText("?x?" + format + fileName);
					mDetails.setTextColor(0xFFFFFFFF);
					finalFormat = format;
				}
				mImageCache.assignImageView(mImageView, null, mCursor.getString(2), null, null, new ImageCache.OnImageAvailableListener() {
					@Override
					public void onImageAvailable(String url, BitmapDrawable bmp, Drawable d, int originalWidth, int originalHeight) {
						if (finalFormat != null) {
							if (mimeType.startsWith("video/") && (originalWidth >= 1280 || originalHeight >= 1024))
								mDetails.setTextColor(0xFFFF0000);
							else
								mDetails.setTextColor(0xFFFFFFFF);
							String gifInfo = "";
							if (d instanceof GifDrawable)
								gifInfo = DateUtils.formatElapsedTime(((GifDrawable) d).getDuration() / 1000) + "." + Integer.toString(((GifDrawable) d).getDuration() % 1000 + 1000).substring(1, 3) + " (" + ((GifDrawable) d).getNumberOfFrames() + ")\n";
							mDetails.setText(originalWidth + "x" + originalHeight + finalFormat + gifInfo + fileName);
						}
					}

					@Override
					public void onImageUnavailable(String url, int reason) {
						if (!mNoIds.contains(id)) {
							mNoIds.add(id);
							refill();
						}
					}
				});
				mImageCache.assignStatusIndicator(mProgressView, mCursor.getString(2));
			}

			@Override
			public void onClick(View v) {
				if (getAdapterPosition() == 0) {
					if (v.equals(mPickImage)) {
						Intent newImageIntent = new Intent(Intent.ACTION_GET_CONTENT);
						newImageIntent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
						newImageIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
						newImageIntent.setType("image/*");
						if (newImageIntent.resolveActivity(v.getContext().getPackageManager()) == null) {
							Toast.makeText(v.getContext(), R.string.new_tweet_attach_no_app_choose_picture, Toast.LENGTH_LONG).show();
							return;
						}
						startActivityForResult(newImageIntent, PICK_MEDIA);
					} else if (v.equals(mPickVideo)) {
						Intent newImageIntent = new Intent(Intent.ACTION_GET_CONTENT);
						newImageIntent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
						newImageIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
						newImageIntent.setType("video/*");
						if (newImageIntent.resolveActivity(v.getContext().getPackageManager()) == null) {
							Toast.makeText(v.getContext(), R.string.new_tweet_attach_no_app_choose_video, Toast.LENGTH_LONG).show();
							return;
						}
						startActivityForResult(newImageIntent, PICK_MEDIA);
					} else if (v.equals(mTakeImage)) {
						Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
						if (takePictureIntent.resolveActivity(v.getContext().getPackageManager()) == null) {
							Toast.makeText(v.getContext(), R.string.new_tweet_attach_no_app_take_picture, Toast.LENGTH_LONG).show();
							return;
						}
						createImageFile();
						takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(mCurrentPhotoPath));
						startActivityForResult(takePictureIntent, PICK_MEDIA);
					} else if (v.equals(mTakeVideo)) {
						Intent takePictureIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
						if (takePictureIntent.resolveActivity(v.getContext().getPackageManager()) == null) {
							Toast.makeText(v.getContext(), R.string.new_tweet_attach_no_app_take_video, Toast.LENGTH_LONG).show();
							return;
						}
						startActivityForResult(takePictureIntent, PICK_MEDIA);
					}
					return;
				}
				if (getAdapterPosition() <= 0 || getAdapterPosition() > getItemCount())
					return;
				mCursor.moveToPosition(getAdapterPosition() - 1);
				TemplateTweetAttachment a = new TemplateTweetAttachment(Uri.fromFile(new File(mCursor.getString(2))), getActivity().getContentResolver(), mTemplateTweet);
				mAttachmentAdapter.insertItem(a);
				a.resolve(getActivity(), mAttachmentAdapter);
				switchViews(null);
			}

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				filterType = position;
				refill();
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {

			}

			@Override
			public boolean onLongClick(final View v) {
				mCursor.moveToPosition(getAdapterPosition() - 1);
				final File f = new File(mCursor.getString(2));
				final long id = mCursor.getLong(0);
				new AlertDialog.Builder(v.getContext())
						.setMessage(getString(R.string.new_tweet_attach_remove_file).replace("%", f.getAbsolutePath()))
						.setNegativeButton(android.R.string.no, null)
						.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								String ext = mEditorPreference.getString(PREF_EXT_MEDIA_STORAGE, null);
								if (!FileTools.deleteFile(f, v.getContext(), ext == null ? null : Uri.parse(ext))) {
									if (ext == null) {
										if (Build.VERSION.SDK_INT >= 19) {
											Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
											if (intent.resolveActivity(v.getContext().getPackageManager()) != null) {
												startActivityForResult(intent, REQUEST_EXTSDCARD_PERMISSION);
												Toast.makeText(v.getContext(), R.string.new_tweet_attach_remove_select_extsdcard, Toast.LENGTH_SHORT).show();
											}
										} else
											Toast.makeText(v.getContext(), R.string.new_tweet_attach_remove_failed, Toast.LENGTH_SHORT).show();
									} else
										Toast.makeText(v.getContext(), R.string.new_tweet_attach_remove_failed, Toast.LENGTH_SHORT).show();
								} else {
									MediaScannerConnection.scanFile(v.getContext(), new String[]{f.getAbsolutePath()}, null, null);
									getActivity().sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(f)));
									mNoIds.add(id);
									refill();
								}
							}
						})
						.show();
				return true;
			}
		}
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
			if (mViewSelectedUserList.getHeight() == 0)
				return;
			scale = Math.max(1, mViewSelectedUserList.getWidth() / mViewSelectedUserList.getHeight());
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

		AttachmentAdapter() {
			setHasStableIds(true);
		}

		@Override
		public long getItemId(int position) {
			if (position >= mTemplateTweet.mAttachments.size())
				return -1;
			return mTemplateTweet.mAttachments.get(position).id;
		}

		public boolean canAddMore() {
			if (mTemplateTweet.mAttachments.size() >= 4)
				return false;
			for (TemplateTweetAttachment a : mTemplateTweet.mAttachments)
				if (a.media_type != TemplateTweetProvider.MEDIA_TYPE_IMAGE)
					return false;
			return true;
		}

		public void removeAll() {
			boolean wasAvailable = canAddMore();
			getActivity().getContentResolver().delete(TemplateTweetProvider.URI_ATTACHMENTS, "template_id=?", new String[]{Long.toString(mTemplateTweet.id)});
			mTemplateTweet.mAttachments.clear();
			if (!wasAvailable && canAddMore())
				notifyDataSetChanged();
		}

		public void removeItem(int index) {
			getActivity().getContentResolver().delete(TemplateTweetProvider.URI_ATTACHMENTS, "_id=?", new String[]{Long.toString(mTemplateTweet.mAttachments.remove(index).id)});
			notifyDataSetChanged();
		}

		public void insertItem(TemplateTweetAttachment a) {
			if (!canAddMore())
				return;
			mTemplateTweet.mAttachments.add(a);
			notifyDataSetChanged();
		}

		@Override
		public int getItemViewType(int position) {
			return R.layout.col_templatetweet_attached_item;
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
			return mTemplateTweet == null ? 0 : mTemplateTweet.mAttachments.size() + (canAddMore() ? 1 : 0);
		}

		@Override
		public void onResolved(TemplateTweetAttachment attachment) {
			if (!mTemplateTweet.mAttachments.contains(attachment))
				return;
			boolean isImageMedia = true;
			for (TemplateTweetAttachment a : mTemplateTweet.mAttachments)
				isImageMedia &= a.media_type == TemplateTweetProvider.MEDIA_TYPE_IMAGE;
			if (mTemplateTweet.mAttachments.size() > 4 || (mTemplateTweet.mAttachments.size() >= 2 && !isImageMedia))
				DarknovaApplication.showToast(R.string.new_tweet_attach_remove_required);
			notifyDataSetChanged();
			mTemplateTweet.updateSelf(getActivity().getContentResolver());
		}

		@Override
		public void onTypeResolved(TemplateTweetAttachment attachment) {
			notifyDataSetChanged();
		}

		@Override
		public void onResolveFailed(TemplateTweetAttachment attachment, Throwable e) {
			e.printStackTrace();
			int index = mTemplateTweet.mAttachments.indexOf(attachment);
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
				if (position >= mTemplateTweet.mAttachments.size()) {
					// add attachment button
					mImageView.setImageDrawable(ResTools.getDrawableByAttribute(getActivity(), R.attr.ic_editor_attach_file));
					mProgressView.setVisibility(View.GONE);
					mTypeView.setImageDrawable(null);
					return;
				}
				if (mImageCache != null) {
					TemplateTweetAttachment a = mTemplateTweet.mAttachments.get(position);
					switch (a.media_type) {
						case TemplateTweetProvider.MEDIA_TYPE_GIF:
						case TemplateTweetProvider.MEDIA_TYPE_VIDEO:
							mTypeView.setImageDrawable(ResTools.getDrawableByAttribute(getActivity(), R.attr.ic_av_videocam));
							break;
						case TemplateTweetProvider.MEDIA_TYPE_IMAGE:
							mTypeView.setImageDrawable(ResTools.getDrawableByAttribute(getActivity(), R.attr.ic_image_image));
							break;
						default:
							mTypeView.setImageDrawable(null);
					}
					if (a.mLocalFileExists) {
						mImageCache.assignImageView(mImageView, a.mLocalFile.getAbsolutePath(), null);
						mImageCache.assignStatusIndicator(mProgressView, a.mLocalFile.getAbsolutePath());
					} else {
						mImageCache.assignImageView(mImageView, null, null);
						mImageCache.assignStatusIndicator(mProgressView, null);
					}
				}
			}

			@SuppressLint("InlinedApi")
			@Override
			public void onClick(View v) {
				final int position = getAdapterPosition();
				if (position >= mTemplateTweet.mAttachments.size()) {
					loadExtra();
					switchViews(mViewMediaList);
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
