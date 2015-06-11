package com.soreepeong.darknova.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import com.soreepeong.darknova.R;
import com.soreepeong.darknova.ui.fragments.TemplateTweetEditorFragment;

/**
 * @author Soreepeong
 */
public class TemplateTweetActivity extends AppCompatActivity implements MultiContentFragmentActivity, TemplateTweetEditorFragment.OnNewTweetVisibilityChangedListener {

	TemplateTweetEditorFragment mEditorFragment;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_templatetweet);
		setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
		ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setDisplayUseLogoEnabled(true);
		//*
		mEditorFragment = (TemplateTweetEditorFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_template_tweet);
		mEditorFragment.initializeNewTweet();
		mEditorFragment.setOnNewTweetVisibilityChangedListener(this);
		//*/
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				Intent upIntent = NavUtils.getParentActivityIntent(this);
				if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
					TaskStackBuilder.create(this).addNextIntentWithParentStack(upIntent).startActivities();
				} else {
					NavUtils.navigateUpTo(this, upIntent);
				}
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void hideContents() {

	}

	@Override
	public void showContents() {

	}

	@Override
	public void onNewTweetVisibilityChanged(boolean visible) {
		if (!visible)
			finish();
	}

	@Override
	public void onBackPressed() {
		if (mEditorFragment.switchViews(null))
			return;
		super.onBackPressed();
	}
}
