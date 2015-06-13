package com.soreepeong.darknova.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;

import com.soreepeong.darknova.R;
import com.soreepeong.darknova.settings.TemplateTweet;
import com.soreepeong.darknova.settings.TemplateTweetAttachment;
import com.soreepeong.darknova.ui.adapters.TemplateAdapter;
import com.soreepeong.darknova.ui.fragments.TemplateTweetEditorFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Soreepeong
 */
public class ShareReceiverActivity extends AppCompatActivity implements TemplateAdapter.OnTemplateTweetSelectedListener, View.OnClickListener {

	private RecyclerView mTemplateList;
	private LinearLayoutManager mLayoutManager;
	private TemplateAdapter mAdapter;
	private ArrayList<SharedItems> mSharedItems;
	private Button mCancel, mNewTemplate;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		String action = intent.getAction();
		String type = intent.getType();

		mSharedItems = new ArrayList<>();
		if (Intent.ACTION_SEND.equals(action) && type != null) {
			if ("text/plain".equals(type)) {
				if (intent.getStringExtra(Intent.EXTRA_TEXT) != null)
					mSharedItems.add(new SharedItems(null, type, intent.getStringExtra(Intent.EXTRA_TEXT)));
			} else if (type.startsWith("image/") || type.startsWith("video/")) {
				Uri u = intent.getParcelableExtra(Intent.EXTRA_STREAM);
				if (u != null)
					mSharedItems.add(new SharedItems(u, type, null));
			}
		} else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
			if (type.startsWith("image/") || type.startsWith("video/")) {
				ArrayList<Uri> urls = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
				if (urls != null)
					for (Uri u : urls) {
						if (u != null)
							mSharedItems.add(new SharedItems(u, type, null));
					}
			}
		}
		if (mSharedItems.isEmpty()) {
			Intent i = new Intent(this, MainActivity.class);
			i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(i);
			finish();
		} else {
			while (mSharedItems.size() > 4)
				mSharedItems.remove(mSharedItems.size() - 1);
			setContentView(R.layout.activity_sharereceiver);
			mCancel = (Button) findViewById(R.id.cancel);
			mNewTemplate = (Button) findViewById(R.id.add_to_new);
			mCancel.setOnClickListener(this);
			mNewTemplate.setOnClickListener(this);
			mTemplateList = (RecyclerView) findViewById(R.id.list);
			mTemplateList.setLayoutManager(mLayoutManager = new LinearLayoutManager(this));
			mTemplateList.setAdapter(mAdapter = new TemplateAdapter(this, this));
		}
	}

	private void addToTemplate(final TemplateTweet t) {
		for (SharedItems i : mSharedItems) {
			if (i.data != null) {
				if (!t.text.matches("^(.*\\s)?"))
					t.text += " ";
				t.text += i.data;
				continue;
			}
			TemplateTweetAttachment attachment = new TemplateTweetAttachment(i.uri, getContentResolver(), t);
			attachment.resolve(this, new TemplateTweetAttachment.AttachmentResolveResult() {
				@Override
				public void onResolved(TemplateTweetAttachment attachment) {
				}

				@Override
				public void onTypeResolved(TemplateTweetAttachment attachment) {
				}

				@Override
				public void onResolveFailed(TemplateTweetAttachment attachment, Throwable e) {
					t.mAttachments.remove(attachment);
					t.updateSelf(getContentResolver());
				}
			});
			t.mAttachments.add(attachment);
		}
		t.updateSelf(getContentResolver());
		new AlertDialog.Builder(this)
				.setNegativeButton(android.R.string.no, null)
				.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						PreferenceManager.getDefaultSharedPreferences(ShareReceiverActivity.this).edit().putLong(TemplateTweetEditorFragment.PREF_LAST_WORKING_TEMPLATE_ID, t.id).apply();
						startActivity(new Intent(ShareReceiverActivity.this, TemplateTweetActivity.class));
					}
				})
				.setNeutralButton(R.string.new_tweet_in_main, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						PreferenceManager.getDefaultSharedPreferences(ShareReceiverActivity.this).edit().putLong(TemplateTweetEditorFragment.PREF_LAST_WORKING_TEMPLATE_ID, t.id).apply();
						Intent i = new Intent(ShareReceiverActivity.this, MainActivity.class);
						i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						startActivity(i);
					}
				}).setCancelable(true)
				.setOnDismissListener(new DialogInterface.OnDismissListener() {
					@Override
					public void onDismiss(DialogInterface dialog) {
						finish();
					}
				})
				.setMessage(R.string.new_tweet_open_composer)
				.show();
	}

	@Override
	public void onTemplateTweetSelected(TemplateAdapter adapter, TemplateTweet t) {
		addToTemplate(t);
	}

	@Override
	public void onClick(View v) {
		if (v.equals(mCancel))
			finish();
		else if (v.equals(mNewTemplate)) {
			long lastTweetId = PreferenceManager.getDefaultSharedPreferences(this).getLong(TemplateTweetEditorFragment.PREF_LAST_WORKING_TEMPLATE_ID, -1);
			List<Long> mIds = null;
			if (lastTweetId != -1) {
				try {
					mIds = new TemplateTweet(lastTweetId, getContentResolver()).mUserIdList;
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
			TemplateTweet newTweet = new TemplateTweet(getContentResolver(), mIds);
			PreferenceManager.getDefaultSharedPreferences(this).edit().putLong(TemplateTweetEditorFragment.PREF_LAST_WORKING_TEMPLATE_ID, newTweet.id).apply();
			addToTemplate(newTweet);
		}
	}

	private class SharedItems {
		final Uri uri;
		final String mimeType;
		final String data;

		SharedItems(Uri u, String mime, String text) {
			uri = u;
			mimeType = mime;
			data = text;
		}
	}
}
