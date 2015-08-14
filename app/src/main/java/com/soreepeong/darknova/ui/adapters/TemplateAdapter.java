package com.soreepeong.darknova.ui.adapters;

import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Build;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.soreepeong.darknova.R;
import com.soreepeong.darknova.core.ImageCache;
import com.soreepeong.darknova.drawable.SquarePatchDrawable;
import com.soreepeong.darknova.services.TemplateTweetProvider;
import com.soreepeong.darknova.settings.TemplateTweet;
import com.soreepeong.darknova.settings.TemplateTweetAttachment;
import com.soreepeong.darknova.tools.ResTools;
import com.soreepeong.darknova.twitter.TwitterEngine;

/**
 * @author Soreepeong
 */
public class TemplateAdapter extends RecyclerView.Adapter<TemplateAdapter.ViewHolder> implements ImageCache.OnImageCacheReadyListener {
	private final ContentResolver mResolver;
	private final SparseArray<TemplateTweet> mTemplates = new SparseArray<>();
	private final String[] mTemplateTypes;
	private final ImageCache mImageCache;
	private final Context mContext;
	private final OnTemplateTweetSelectedListener mListener;
	private Cursor mCursor;
	private long mExcludedId = -1;
	private final ContentObserver mObserver = new ContentObserver(new Handler()) {
		@Override
		public void onChange(boolean selfChange) {
			refill();
		}
	};

	public TemplateAdapter(Context context, OnTemplateTweetSelectedListener listener) {
		setHasStableIds(true);
		mContext = context;
		mResolver = context.getContentResolver();
		mResolver.registerContentObserver(TemplateTweetProvider.URI_BASE, true, mObserver);
		mTemplateTypes = context.getResources().getStringArray(R.array.new_tweet_type_array);
		mImageCache = ImageCache.getCache(context, this);
		mListener = listener;
		refill();
	}

	public void setExcludedId(long n) {
		mExcludedId = n;
		refill();
	}

	public void refill() {
		mTemplates.clear();
		if (mCursor != null)
			mCursor.close();
		mCursor = mResolver.query(TemplateTweetProvider.URI_TEMPLATES, null, "_id!=?", new String[]{Long.toString(mExcludedId)}, null);
		notifyDataSetChanged();
	}

	public void done() {
		if (mCursor != null)
			mCursor.close();
		mResolver.unregisterContentObserver(mObserver);
	}

	@Override
	public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.row_template_tweet, parent, false));
	}

	public TemplateTweet getItem(int position) {
		TemplateTweet t = mTemplates.get(position);
		if (t == null) {
			mCursor.moveToPosition(position);
			mTemplates.put(position, t = TemplateTweet.obtain(mCursor, mResolver));
		}
		return t;
	}

	@Override
	public void onBindViewHolder(ViewHolder holder, int position) {
		TemplateTweet t = getItem(position);
		if (t == null) {
			mCursor.moveToPosition(position);
			mTemplates.put(position, t = TemplateTweet.obtain(mCursor, mResolver));
		}
		StringBuilder sb;
		sb = new StringBuilder();
		switch (t.type) {
			case TemplateTweetProvider.TEMPLATE_TYPE_ONESHOT:
				sb.append(mTemplateTypes[0]);
				break;
			case TemplateTweetProvider.TEMPLATE_TYPE_SCHEDULED:
				sb.append(mTemplateTypes[1]);
				break;
			case TemplateTweetProvider.TEMPLATE_TYPE_PERIODIC:
				sb.append(mTemplateTypes[2]);
				break;
			case TemplateTweetProvider.TEMPLATE_TYPE_REPLY:
				sb.append(mTemplateTypes[3]);
				break;
		}
		sb.append(" / ").append(t.enabled ? mContext.getString(R.string.new_tweet_enabled) : mContext.getString(R.string.new_tweet_disabled));
		if (t.remove_after)
			sb.append(" / ").append(mContext.getString(R.string.new_tweet_remove_after));
		holder.type.setText(sb.toString());
		sb = new StringBuilder();
		SquarePatchDrawable dr = new SquarePatchDrawable();
		for (long user_id : t.mUserIdList) {
			TwitterEngine e = TwitterEngine.get(user_id);
			if (sb.length() > 0)
				sb.append(", ");
			if (e == null) {
				sb.append("(").append(user_id).append(")");
				dr.addDrawable(ResTools.getDrawableByAttribute(mContext, R.attr.ic_content_clear));
			} else {
				sb.append(e.getScreenName());
				if (mImageCache != null)
					dr.addDrawable(mImageCache.getDrawable(e.getTweeter().getProfileImageUrl(), holder.picture.getLayoutParams().width, holder.picture.getLayoutParams().height, null));
			}
		}
		holder.picture.setImageDrawable(dr);
		holder.username.setText(sb.toString());
		holder.data.setText(t.text);
		switch (t.type) {
			case TemplateTweetProvider.TEMPLATE_TYPE_ONESHOT:
				holder.reply_info.setVisibility(View.GONE);
				holder.description.setVisibility(View.GONE);
				break;
			case TemplateTweetProvider.TEMPLATE_TYPE_SCHEDULED:
				holder.reply_info.setVisibility(View.GONE);
				holder.description.setVisibility(View.VISIBLE);
				break;
			case TemplateTweetProvider.TEMPLATE_TYPE_PERIODIC:
				holder.reply_info.setVisibility(View.GONE);
				holder.description.setVisibility(View.VISIBLE);
				break;
			case TemplateTweetProvider.TEMPLATE_TYPE_REPLY:
				holder.reply_info.setVisibility(View.VISIBLE);
				holder.description.setVisibility(View.VISIBLE);
				break;
		}


		if (mImageCache != null) {
			holder.mAttachmentView.setVisibility(t.mAttachments.isEmpty() ? View.GONE : View.VISIBLE);
			int i = 0;
			for (; i < holder.mImageView.length && i < t.mAttachments.size(); i++) {
				holder.mAttachmentView.getChildAt(i).setVisibility(View.VISIBLE);
				if (Build.VERSION.SDK_INT >= 16)
					holder.mAttachmentView.getChildAt(i).setBackground(null);
				else
					//noinspection deprecation
					holder.mAttachmentView.getChildAt(i).setBackgroundDrawable(null);
				TemplateTweetAttachment a = t.mAttachments.get(i);
				switch (a.media_type) {
					case TemplateTweetProvider.MEDIA_TYPE_GIF:
					case TemplateTweetProvider.MEDIA_TYPE_VIDEO:
						holder.mTypeView[i].setImageDrawable(ResTools.getDrawableByAttribute(mContext, R.attr.ic_av_videocam));
						break;
					case TemplateTweetProvider.MEDIA_TYPE_IMAGE:
						holder.mTypeView[i].setImageDrawable(ResTools.getDrawableByAttribute(mContext, R.attr.ic_image_image));
						break;
					default:
						holder.mTypeView[i].setImageDrawable(null);
				}
				if (a.mLocalFileExists) {
					mImageCache.assignImageView(holder.mImageView[i], a.mLocalFile.getAbsolutePath(), null);
					mImageCache.assignStatusIndicator(holder.mProgressView[i], a.mLocalFile.getAbsolutePath());
				} else {
					mImageCache.assignImageView(holder.mImageView[i], null, null);
					mImageCache.assignStatusIndicator(holder.mProgressView[i], null);
				}
			}
			for (; i < holder.mImageView.length; i++) {
				holder.mAttachmentView.getChildAt(i).setVisibility(View.GONE);
				mImageCache.assignImageView(holder.mImageView[i], null, null);
				mImageCache.assignStatusIndicator(holder.mProgressView[i], null);
			}
		}
	}

	@Override
	public long getItemId(int position) {
		return getItem(position).id;
	}

	@Override
	public int getItemCount() {
		return mCursor.getCount();
	}

	@Override
	public void onImageCacheReady(ImageCache cache) {
		notifyDataSetChanged();
	}

	public interface OnTemplateTweetSelectedListener {
		void onTemplateTweetSelected(TemplateAdapter adapter, TemplateTweet t);
	}

	public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
		TextView type, username, data, description, reply_info;
		ImageView picture;
		ViewGroup mAttachmentView;
		ImageView mImageView[];
		ImageView mTypeView[];
		View mProgressView[];

		public ViewHolder(View itemView) {
			super(itemView);
			itemView.setOnClickListener(this);
			itemView.setOnLongClickListener(this);
			type = (TextView) itemView.findViewById(R.id.type);
			picture = (ImageView) itemView.findViewById(R.id.imgUserPictureFull);
			username = (TextView) itemView.findViewById(R.id.user_name);
			data = (TextView) itemView.findViewById(R.id.lblData);
			description = (TextView) itemView.findViewById(R.id.lblDescription);
			reply_info = (TextView) itemView.findViewById(R.id.reply_info);
			mAttachmentView = (ViewGroup) itemView.findViewById(R.id.attachments);
			mImageView = new ImageView[mAttachmentView.getChildCount()];
			mTypeView = new ImageView[mAttachmentView.getChildCount()];
			mProgressView = new View[mAttachmentView.getChildCount()];
			for (int i = 0; i < mImageView.length; i++) {
				mImageView[i] = (ImageView) mAttachmentView.getChildAt(i).findViewById(R.id.image);
				mProgressView[i] = mAttachmentView.getChildAt(i).findViewById(R.id.progress);
				mTypeView[i] = (ImageView) mAttachmentView.getChildAt(i).findViewById(R.id.type);
			}
		}

		@Override
		public void onClick(View v) {
			TemplateTweet t = getItem(getAdapterPosition());
			mListener.onTemplateTweetSelected(TemplateAdapter.this, t);
		}

		@Override
		public boolean onLongClick(View v) {
			new AlertDialog.Builder(v.getContext())
					.setMessage(R.string.new_tweet_remove)
					.setNegativeButton(android.R.string.no, null)
					.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							TemplateTweet t = getItem(getAdapterPosition());
							t.removeSelf(mResolver);
						}
					})
					.show();
			return true;
		}
	}
}
