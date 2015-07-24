package com.soreepeong.darknova.ui.fragments;

import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.soreepeong.darknova.R;
import com.soreepeong.darknova.core.HTTPRequest;
import com.soreepeong.darknova.core.ImageCache;
import com.soreepeong.darknova.extractors.ImageExtractor;
import com.soreepeong.darknova.tools.FileTools;
import com.soreepeong.darknova.tools.ResTools;
import com.soreepeong.darknova.tools.StreamTools;
import com.soreepeong.darknova.tools.StringTools;
import com.soreepeong.darknova.ui.MediaPreviewActivity;
import com.soreepeong.darknova.ui.view.LargeImageView;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.regex.Matcher;

/**
 * Fragment that displays media.
 *
 * @author Soreepeong
 */
public class MediaFragment extends Fragment implements View.OnClickListener, LargeImageView.OnImageViewLoadFinishedListener, ImageCache.OnImageCacheReadyListener, LargeImageView.OnViewerParamChangedListener, MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, SurfaceHolder.Callback, MediaPlayer.OnVideoSizeChangedListener {
	private static final Interpolator mProgressInterpolator = new DecelerateInterpolator();

	private static final int STATE_ERROR = -1;
	private static final int STATE_IDLE = 0;
	private static final int STATE_PREPARING = 1;
	private static final int STATE_PREPARED = 2;
	private static final int STATE_PLAYING = 3;
	private static final int STATE_PAUSED = 4;

	public MediaPreviewActivity.Image mImage;
	public File mImageFile;
	public boolean mSaveCopy;
	int mMediaPlayerStatus;
	private View mViewFragmentRoot;
	private SurfaceView mViewSurface;
	private ImageCache mImageCache;
	private LargeImageView mViewImageViewer;
	private View mViewLoadInfo, mViewPageInfo;
	private TextView mViewLoadInfoText, mViewLoadProgressText, mViewPageZoom;
	private ProgressBar mViewProgress;
	private ProgressBar mViewProgressTop;
	private Button mViewCancelButton, mViewAnotherAppButton;
	private SurfaceHolder mSurface;
	private String mVideoLocation;
	private MediaPlayer mMediaPlayer;
	private ImageLoaderTask mImageLoader;
	private boolean mLoadOriginalMedia;
	private boolean mLoadInfoVisible;

	/**
	 * Make new instanceof this fragment using given parameter
	 *
	 * @param image Image to show
	 * @return New fragment
	 */
	public static MediaFragment newInstance(MediaPreviewActivity.Image image) {
		MediaFragment frag = new MediaFragment();
		frag.mImage = image;
		return frag;
	}

	/**
	 * Is MediaPlayer ready?
	 *
	 * @return true if ready
	 */
	private boolean isInPlaybackState() {
		return (mMediaPlayer != null &&
				mMediaPlayerStatus != STATE_ERROR &&
				mMediaPlayerStatus != STATE_IDLE &&
				mMediaPlayerStatus != STATE_PREPARING);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		if (container == null)
			return null;
		mViewFragmentRoot = inflater.inflate(R.layout.fragment_media, container, false);
		mViewImageViewer = (LargeImageView) mViewFragmentRoot.findViewById(R.id.viewer);
		mViewPageInfo = mViewFragmentRoot.findViewById(R.id.divPageInfo);
		mViewPageZoom = (TextView) mViewFragmentRoot.findViewById(R.id.zoom);
		mViewLoadInfo = mViewFragmentRoot.findViewById(R.id.divLoadInfo);
		mViewLoadInfoText = (TextView) mViewFragmentRoot.findViewById(R.id.lblLoadInfo);
		mViewLoadProgressText = (TextView) mViewFragmentRoot.findViewById(R.id.lblLoadProgress);
		mViewProgress = (ProgressBar) mViewFragmentRoot.findViewById(R.id.pageProgress);
		mViewProgressTop = (ProgressBar) mViewFragmentRoot.findViewById(R.id.pageProgressTop);
		mViewCancelButton = (Button) mViewFragmentRoot.findViewById(R.id.cancel);
		mViewAnotherAppButton = (Button) mViewFragmentRoot.findViewById(R.id.open_another_app);
		mViewSurface = (SurfaceView) mViewFragmentRoot.findViewById(R.id.viewerVideo);
		applyInfoVisibility(true);
		mViewImageViewer.setOnClickListener(this);
		mViewImageViewer.setOnImageViewLoadFinsihedListener(this);
		mViewImageViewer.setOnViewerParamChangedListener(this);
		mViewCancelButton.setOnClickListener(this);
		mViewAnotherAppButton.setOnClickListener(this);
		mViewSurface.getHolder().addCallback(this);
		mImageCache = ImageCache.getCache(getActivity(), this);
		return mViewFragmentRoot;
	}

	private void initMediaPlayer() {
		if (getView() == null)
			return;
		if (mVideoLocation == null)
			return;
		else
			mViewSurface.setVisibility(View.VISIBLE);
		if (mSurface == null) // Will be called again when surface is ready
			return;
		clearMediaPlayer();
		MediaPlayer player = new MediaPlayer();
		try {
			player.setOnPreparedListener(this);
			player.setOnErrorListener(this);
			player.setLooping(true);
			player.setOnVideoSizeChangedListener(this);
			player.setDisplay(mViewSurface.getHolder());
			player.setDataSource(mVideoLocation);
			player.prepareAsync();
			mMediaPlayerStatus = STATE_PREPARING;
			mMediaPlayer = player;
		} catch (Exception e) {
			e.printStackTrace();
			player.release();
			mMediaPlayerStatus = STATE_ERROR;
		}
	}

	public void clearMediaPlayer() {
		if (mMediaPlayer != null) {
			mMediaPlayer.release();
			mMediaPlayer = null;
			mMediaPlayerStatus = STATE_IDLE;
		}
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		mViewFragmentRoot = null;
		mViewImageViewer = null;
		mViewLoadInfo = null;
		mViewLoadInfoText = null;
		mViewLoadProgressText = null;
		mViewProgress = null;
		mViewProgressTop = null;
		mViewCancelButton = null;
		mViewAnotherAppButton = null;
		mViewSurface = null;
	}

	private void startLoaderTask(boolean bUpgradeQuality) {
		mLoadOriginalMedia |= bUpgradeQuality;
		if (mImageLoader != null)
			mImageLoader.cancel(true);
		mImageLoader = new ImageLoaderTask();
		mImageLoader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mLoadOriginalMedia ? mImage.mOriginalUrl : mImage.mResizedUrl);
		mLoadInfoVisible = true;
		applyInfoVisibility(false);
	}

	@Override
	public void onClick(View v) {
		if (v.equals(mViewCancelButton)) {
			if (mImageLoader == null) {
				startLoaderTask(true);
			} else {
				mImageLoader.cancel(true);
				if (mViewImageViewer.isLoaded()) {
					mLoadInfoVisible = false;
					applyInfoVisibility(false);
				} else
					mViewCancelButton.setText(R.string.mediapreview_retry);
				mImageLoader = null;
			}
		} else if (v.equals(mViewImageViewer)) {
			((MediaPreviewActivity) getActivity()).toggleActionBarVisibility(mImage);
		} else if (v.equals(mViewAnotherAppButton)) {
			onOptionsItemSelected(R.id.open);
		}
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
		mMediaPlayerStatus = STATE_PREPARED;
		if (mImageLoader != null) {
			clearMediaPlayer();
			return;
		}
		if (getView() == null)
			return;
		((MediaPreviewActivity) getActivity()).hideActionBarDelayed(mImage);
		mViewImageViewer.loadEmptyArea(mp.getVideoWidth(), mp.getVideoHeight());
		mLoadInfoVisible = false;
		applyInfoVisibility(false);
		mp.start();
		mMediaPlayerStatus = STATE_PLAYING;
	}

	private void applyInfoVisibility(boolean immediate) {
		if (getView() == null)
			return;
		if (!(mLoadInfoVisible && ((MediaPreviewActivity) getActivity()).isActionBarVisible()) && mViewImageViewer.isLoaded() && mViewLoadInfo.getVisibility() == View.VISIBLE) {
			if (immediate)
				mViewLoadInfo.setVisibility(View.GONE);
			else
				ResTools.hideWithAnimation(mViewLoadInfo, android.R.anim.fade_out, true);
		} else if (((mLoadInfoVisible && ((MediaPreviewActivity) getActivity()).isActionBarVisible()) || !mViewImageViewer.isLoaded()) && mViewLoadInfo.getVisibility() != View.VISIBLE) {
			if (immediate)
				mViewLoadInfo.setVisibility(View.VISIBLE);
			else {
				mViewLoadInfo.setVisibility(View.VISIBLE);
				mViewLoadInfo.clearAnimation();
				mViewLoadInfo.startAnimation(AnimationUtils.loadAnimation(getActivity(), android.R.anim.fade_in));
			}
		}
		if (!(mViewImageViewer.isLoaded() && ((MediaPreviewActivity) getActivity()).isActionBarVisible()) && mViewPageInfo.getVisibility() == View.VISIBLE) {
			if (immediate)
				mViewPageInfo.setVisibility(View.GONE);
			else
				ResTools.hideWithAnimation(mViewPageInfo, android.R.anim.fade_out, true);
		} else if (mViewImageViewer.isLoaded() && ((MediaPreviewActivity) getActivity()).isActionBarVisible() && mViewPageInfo.getVisibility() != View.VISIBLE) {
			if (immediate)
				mViewPageInfo.setVisibility(View.VISIBLE);
			else {
				mViewPageInfo.setVisibility(View.VISIBLE);
				mViewPageInfo.clearAnimation();
				mViewPageInfo.startAnimation(AnimationUtils.loadAnimation(getActivity(), android.R.anim.fade_in));
			}
		}
	}

	public void onActionBarShown() {
		applyInfoVisibility(false);
	}

	public void onActionBarHidden() {
		applyInfoVisibility(false);
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		mMediaPlayerStatus = STATE_ERROR;
		return true;
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		mSurface = holder;
		initMediaPlayer();
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		if (getView() == null)
			return;
		if (isInPlaybackState()) {
			mMediaPlayer.start();
			mMediaPlayerStatus = STATE_PLAYING;
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		mSurface = null;
		clearMediaPlayer();
	}

	@Override
	public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
		if (getView() == null)
			return;
		mViewSurface.getHolder().setFixedSize(width, height);
	}

	@Override
	public void onPause() {
		clearMediaPlayer();
		super.onPause();
	}

	@Override
	public void onResume() {
		if (getView() == null)
			return;
		initMediaPlayer();
		if (mImage.mIsViewerPreviouslySet) {
			if (!mViewImageViewer.isLoaded())
				mViewImageViewer.loadEmptyArea(mImage.mViewerWidth, mImage.mViewerHeight);
			mViewImageViewer.setViewerParams(mImage.mViewerX, mImage.mViewerY, mImage.mViewerZoom);
			mViewImageViewer.setDirection(mImage.mViewerDirection);
		}
		super.onResume();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putParcelable("image", mImage);
	}

	@Override
	public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
		super.onViewStateRestored(savedInstanceState);
		if (savedInstanceState != null) {
			mImage = savedInstanceState.getParcelable("image");
		}
	}

	public void onPageEnter() {
		if (getView() == null)
			return;
		if (mViewImageViewer.isLoaded()) {
			((MediaPreviewActivity) getActivity()).hideActionBarDelayed(mImage);
		}
		mViewImageViewer.triggerOnImageParamChangedListener();
		initMediaPlayer();
	}

	public void resetPosition() {
		if (getView() == null)
			return;
		if (mViewImageViewer.isLoaded()) {
			mViewImageViewer.resetPosition(true);
		}
	}

	public void onPageLeave() {
		clearMediaPlayer();
	}

	public void onOptionsItemSelected(int id) {
		switch (id) {
			case R.id.rotate_left: {
				mViewImageViewer.rotateLeft();
				break;
			}
			case R.id.rotate_right: {
				mViewImageViewer.rotateRight();
				break;
			}
			case R.id.download: {
				if (mImageFile != null && mImageFile.exists() && mLoadOriginalMedia) {
					String downloadFileName = "";
					String extension = "";
					Matcher m = ImageExtractor.mFileNameGetter.matcher(mImage.mOriginalUrl);
					if (m.matches()) {
						downloadFileName = m.group(1);
						extension = m.group(2);
					}
					if (downloadFileName.trim().length() == 0)
						downloadFileName = mImage.mOriginalUrl;
					downloadFileName = ImageExtractor.mFileNameReplacer.matcher(downloadFileName).replaceAll("_");
					File newFile;
					int i = 0;
					do {
						newFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), downloadFileName + (i == 0 ? "" : "-" + i) + extension);
						i++;
					} while (newFile.exists());
					try {
						FileTools.copyFile(mImageFile, newFile);
						final File mDownloadedFile = newFile;
						new AlertDialog.Builder(getActivity())
								.setMessage(StringTools.fillStringResFormat(getActivity(), R.string.mediapreview_downloaded_ask, "path", mDownloadedFile.getAbsolutePath()))
								.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog, int which) {
										Intent i = new Intent(Intent.ACTION_VIEW);
										i.setDataAndType(Uri.fromFile(mDownloadedFile), mImage.mOriginalContentType == null ? "image/*" : mImage.mOriginalContentType);
										try {
											getActivity().startActivity(i);
										} catch (Exception e) {
											Toast.makeText(getActivity(), R.string.mediapreview_downloaded_ask_fail, Toast.LENGTH_LONG).show();
										}
									}
								})
								.setNegativeButton(android.R.string.no, null)
								.show();
						break;
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				mSaveCopy = true;
				mLoadOriginalMedia = true;
			}
			case R.id.reload: {
				if (mImageLoader != null)
					break;
				startLoaderTask(false);
				break;
			}
			case R.id.share: {
				break;
			}
			case R.id.copy: {
				ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
				clipboard.setPrimaryClip(ClipData.newPlainText(mImage.mSourceUrl, mImage.mSourceUrl));
				break;
			}
			case R.id.copy_image: {
				ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
				clipboard.setPrimaryClip(ClipData.newPlainText(mImage.mOriginalUrl, mImage.mOriginalUrl));
				break;
			}
			case R.id.search_image: {
				getActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://images.google.com/searchbyimage?image_url=" + StringTools.UrlEncode(mImage.mOriginalUrl))));
				break;
			}
			case R.id.open: {
				Uri uri = Uri.parse(((MediaPreviewActivity) getActivity()).mInitiatorUrl);
				PackageManager packageManager = getActivity().getPackageManager();
				ArrayList<Intent> targetIntents = new ArrayList<>();
				for (ResolveInfo currentInfo : packageManager.queryIntentActivities(new Intent(Intent.ACTION_VIEW, uri), 0)) {
					if (!getActivity().getPackageName().equals(currentInfo.activityInfo.packageName)) {
						Intent targetIntent = new Intent(android.content.Intent.ACTION_VIEW, uri);
						targetIntent.setPackage(currentInfo.activityInfo.packageName);
						targetIntents.add(targetIntent);
					}
				}

				if (targetIntents.size() > 0) {
					Intent chooserIntent = Intent.createChooser(targetIntents.remove(0), getString(R.string.mediapreview_open));
					chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, targetIntents.toArray(new Parcelable[targetIntents.size()]));
					getActivity().startActivity(chooserIntent);
				} else
					Toast.makeText(getActivity(), R.string.mediapreview_downloaded_ask_fail, Toast.LENGTH_SHORT).show();
				break;
			}
		}
	}

	@Override
	public void onImageViewLoadFinished(LargeImageView v) {
		if (getView() != null) {
			mLoadInfoVisible = false;
			applyInfoVisibility(false);
			((MediaPreviewActivity) getActivity()).hideActionBarDelayed(mImage);
		}
	}

	@Override
	public void onImageViewLoadFailed(LargeImageView v, Throwable nError) {
		if (getView() != null) {
			mViewLoadInfoText.setText(R.string.mediapreview_error);
			mViewLoadProgressText.setText(nError == null ? "" : nError.toString());
			mViewCancelButton.setText(R.string.mediapreview_retry);
			mViewImageViewer.removeImage();
			mLoadInfoVisible = true;
			applyInfoVisibility(false);
		}
	}

	@Override
	public void onImageCacheReady(ImageCache cache) {
		mImageCache = cache;
		String path;
		path = mImageCache.getCachedImagePath(mImage.mOriginalUrl);
		if (path != null) {
			mImageFile = new File(path);
			if (mImageFile.exists() && getView() != null) {
				mViewImageViewer.removeImage();
				if (mImage.mIsViewerPreviouslySet) {
					if (!mViewImageViewer.isLoaded())
						mViewImageViewer.loadEmptyArea(mImage.mViewerWidth, mImage.mViewerHeight);
					mViewImageViewer.setViewerParams(mImage.mViewerX, mImage.mViewerY, mImage.mViewerZoom);
					mViewImageViewer.setDirection(mImage.mViewerDirection);
				}
				if (mImage.mOriginalContentType != null && mImage.mOriginalContentType.toLowerCase().startsWith("video/")) {
					mVideoLocation = mImageFile.getAbsolutePath();
					initMediaPlayer();
				} else {
					mViewImageViewer.loadImage(mImageFile.getAbsolutePath());
				}
				mLoadOriginalMedia = true;
			}
		}
		boolean resizedExists = false;
		if (!mLoadOriginalMedia && mImage.mResizedUrl != null) {
			path = mImageCache.getCachedImagePath(mImage.mResizedUrl);
			if (path != null && getView() != null) {
				File resizedFile = new File(path);
				if (resizedFile.exists()) {
					mViewLoadInfoText.setText(R.string.mediapreview_reading);
					mViewLoadProgressText.setText(StringTools.fileSize(resizedFile.length()));
					mViewImageViewer.removeImage();
					if (mImage.mIsViewerPreviouslySet) {
						if (!mViewImageViewer.isLoaded())
							mViewImageViewer.loadEmptyArea(mImage.mViewerWidth, mImage.mViewerHeight);
						mViewImageViewer.setViewerParams(mImage.mViewerX, mImage.mViewerY, mImage.mViewerZoom);
						mViewImageViewer.setDirection(mImage.mViewerDirection);
					}
					mViewImageViewer.loadImage(resizedFile.getAbsolutePath());
					resizedExists = true;
				}
			}
		}
		if ((mImage.mOriginalContentType != null && mImage.mOriginalContentType.toLowerCase().startsWith("video/") && !mLoadOriginalMedia) || mImage.mResizedUrl == null)
			startLoaderTask(true);
		else if (!resizedExists && !mLoadOriginalMedia)
			startLoaderTask(false);
		else
			applyInfoVisibility(true);
	}

	@Override
	public void onImageParamChanged(int x, int minX, int maxX, int y, int minY, int maxY, int width, int height, float zoom) {
		if (mViewImageViewer == null)
			return;
		((MediaPreviewActivity) getActivity()).setAtRight(mImage, x <= minX, minX != maxX);
		((MediaPreviewActivity) getActivity()).setAtLeft(mImage, x >= maxX, minX != maxX);
		int w = (int) (width * zoom);
		int h = (int) (height * zoom);
		mImage.mViewerX = x;
		mImage.mViewerY = y;
		mImage.mViewerZoom = zoom;
		mImage.mIsViewerPreviouslySet = true;
		mImage.mViewerDirection = mViewImageViewer.getDirection();
		mImage.mViewerWidth = mViewImageViewer.isRotatedVertically() ? height : width;
		mImage.mViewerHeight = mViewImageViewer.isRotatedVertically() ? width : height;
		if (getView() == null)
			return;
		FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mViewSurface.getLayoutParams();
		lp.topMargin = y - h / 2;
		lp.leftMargin = x - w / 2;
		lp.width = Math.max(1, w);
		lp.height = Math.max(1, h);
		if (minX != maxX) {
			if (lp.leftMargin + lp.width < mViewFragmentRoot.getWidth())
				lp.leftMargin = mViewFragmentRoot.getWidth() - lp.width;
			else if (lp.leftMargin > 0) lp.leftMargin = 0;
		}
		if (minY != maxY) {
			if (lp.topMargin + lp.height < mViewFragmentRoot.getHeight())
				lp.topMargin = mViewFragmentRoot.getHeight() - lp.height;
			else if (lp.topMargin > 0) lp.topMargin = 0;
		}
		mViewSurface.requestLayout();
		mViewPageZoom.setText(String.format(getString(R.string.mediapreview_zoom_display), zoom));
		if (zoom > 4 && !mLoadOriginalMedia && mImageLoader == null) {
			startLoaderTask(true);
		}
	}

	private class ImageLoaderTask extends AsyncTask<String, Object, File> {
		private HTTPRequest mDownloader;
		private Exception mException;
		private long mSize, mReceived;
		private String url;
		private boolean mConnected;
		private ObjectAnimator mProgressAnimator, mProgressAnimatorTop;
		private File mDownloadedFile;

		@Override
		protected File doInBackground(String... params) {
			url = params[0];
			File mTempFile = null;
			InputStream in = null;
			OutputStream out = null;
			int read;
			byte buffer[] = new byte[65536];
			try {
				mDownloader = HTTPRequest.getRequest(params[0], mImage.mAuthInfo, false, null);
				mTempFile = File.createTempFile("downloader", null, mImageCache.getCacheFile());
				publishProgress();
				Thread.sleep(50);
				mDownloader.submitRequest();
				Thread.sleep(50);
				if (mDownloader.getStatusCode() != 200)
					throw new RuntimeException(mDownloader.getWholeData(8192));
				if (mDownloader.getInputLength() > 0)
					mSize = mDownloader.getInputLength();
				if (mDownloader.getContentType() != null && mLoadOriginalMedia)
					mImage.mOriginalContentType = mDownloader.getContentType();
				mConnected = true;
				publishProgress(0);
				in = mDownloader.getInputStream();
				url = mDownloader.getUrl();
				out = new BufferedOutputStream(new FileOutputStream(mTempFile));
				while ((read = in.read(buffer)) > 0 && !isCancelled()) {
					out.write(buffer, 0, read);
					mReceived += read;
					publishProgress();
				}
				if (isCancelled())
					mReceived = 0;
				out.flush();
			} catch (Exception e) {
				e.printStackTrace();
				mException = e;
				mReceived = 0;
			} finally {
				if (mDownloader != null)
					mDownloader.close();
				StreamTools.close(in);
				StreamTools.close(out);
				if (mTempFile != null && mReceived == 0) {
					if (!mTempFile.delete())
						mTempFile.deleteOnExit();
					mTempFile = null;
				}
			}
			if (mTempFile != null && mSaveCopy && mLoadOriginalMedia) {
				String downloadFileName = "";
				String extension = "";
				Matcher m = ImageExtractor.mFileNameGetter.matcher(url);
				if (m.matches()) {
					downloadFileName = m.group(1);
					extension = m.group(2);
				}
				if (downloadFileName.trim().length() == 0)
					downloadFileName = url;
				downloadFileName = ImageExtractor.mFileNameReplacer.matcher(downloadFileName).replaceAll("_");
				File newFile;
				int i = 0;
				do {
					newFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), downloadFileName + (i == 0 ? "" : "-" + i) + extension);
					i++;
				} while (newFile.exists());
				try {
					FileTools.copyFile(mTempFile, newFile);
					mDownloadedFile = newFile;
					mSaveCopy = false;
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return mTempFile;
		}

		@Override
		protected void onProgressUpdate(Object... values) {
			if (getView() == null)
				return;
			if (!mConnected) {
				mViewLoadInfoText.setText(R.string.mediapreview_connecting);
				mViewLoadProgressText.setText("");
			} else {
				if (values.length == 1) {
					mViewLoadInfoText.setText(R.string.mediapreview_downloading);
					mViewProgress.setIndeterminate(mSize == 0);
				}
				if (mSize == 0)
					mViewLoadProgressText.setText(StringTools.fileSize(mReceived));
				else {
					mViewProgress.setIndeterminate(false);
					if (mViewProgressTop.getVisibility() == View.GONE || mViewProgressTop.isIndeterminate()) {
						mViewProgressTop.setIndeterminate(false);
						mViewProgressTop.setVisibility(View.VISIBLE);
					}
					mViewLoadProgressText.setText(StringTools.fileSize(mReceived) + " / " + StringTools.fileSize(mSize));
					if (mProgressAnimator != null)
						mProgressAnimator.cancel();
					mProgressAnimator = ObjectAnimator.ofInt(mViewProgress, "progress", (int) (100000 * (double) mReceived / mSize));
					mProgressAnimator.setDuration(300);
					mProgressAnimator.setInterpolator(mProgressInterpolator);
					mProgressAnimator.start();
					if (mProgressAnimatorTop != null)
						mProgressAnimatorTop.cancel();
					mProgressAnimatorTop = ObjectAnimator.ofInt(mViewProgressTop, "progress", (int) (100000 * (double) mReceived / mSize));
					mProgressAnimatorTop.setDuration(300);
					mProgressAnimatorTop.setInterpolator(mProgressInterpolator);
					mProgressAnimatorTop.start();
				}
			}
		}

		@Override
		protected void onPreExecute() {
			if (getView() == null)
				return;
			mLoadInfoVisible = true;
			applyInfoVisibility(false);
			mViewProgressTop.setVisibility(View.VISIBLE);
			mViewProgressTop.setIndeterminate(true);
			mViewProgressTop.clearAnimation();
			mViewProgressTop.startAnimation(AnimationUtils.loadAnimation(getActivity(), android.R.anim.fade_in));
			mViewProgress.setIndeterminate(true);
			mViewCancelButton.setText(R.string.mediapreview_cancel);
		}

		@Override
		protected void onPostExecute(File file) {
			mImageLoader = null;
			if (getView() == null)
				return;
			if (mSaveCopy) {
				if (mDownloadedFile == null)
					Toast.makeText(getActivity(), R.string.mediapreview_download_fail, Toast.LENGTH_LONG).show();
				else {
					new AlertDialog.Builder(getActivity())
							.setMessage(StringTools.fillStringResFormat(getActivity(), R.string.mediapreview_downloaded_ask, "path", mDownloadedFile.getAbsolutePath()))
							.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									Intent i = new Intent(Intent.ACTION_VIEW);
									i.setDataAndType(Uri.fromFile(mDownloadedFile), mImage.mOriginalContentType == null ? "image/*" : mImage.mOriginalContentType);
									try {
										getActivity().startActivity(i);
									} catch (Exception e) {
										Toast.makeText(getActivity(), R.string.mediapreview_downloaded_ask_fail, Toast.LENGTH_LONG).show();
									}
								}
							})
							.setNegativeButton(android.R.string.no, null)
							.show();
				}
			}
			ResTools.hideWithAnimation(mViewProgressTop, android.R.anim.fade_out, true);
			if (isCancelled()) {
				mViewLoadInfoText.setText(R.string.mediapreview_cancelled);
				mViewLoadProgressText.setText("");
				mViewCancelButton.setText(R.string.mediapreview_retry);
				mLoadInfoVisible = !mViewImageViewer.isLoaded();
			} else if (file == null) {
				mViewLoadInfoText.setText(R.string.mediapreview_error);
				mViewLoadProgressText.setText(mException == null ? "" : mException.toString());
				mViewCancelButton.setText(R.string.mediapreview_retry);
				mLoadInfoVisible = !mViewImageViewer.isLoaded();
			} else {
				String path = mImageCache.getCachedImagePath(url);
				if (path == null)
					path = mImageCache.makeTempPath(url);
				mImageFile = new File(path);
				if (mImageFile.exists())
					mImageFile.delete();
				if (!file.renameTo(mImageFile))
					mImageFile = file;
				mImageCache.applySize(mImageFile);
				mViewLoadInfoText.setText(R.string.mediapreview_reading);
				mViewLoadProgressText.setText(StringTools.fileSize(mImageFile.length()));
				if (mImage.mIsViewerPreviouslySet) {
					if (!mViewImageViewer.isLoaded())
						mViewImageViewer.loadEmptyArea(mImage.mViewerWidth, mImage.mViewerHeight);
					mViewImageViewer.setViewerParams(mImage.mViewerX, mImage.mViewerY, mImage.mViewerZoom);
					mViewImageViewer.setDirection(mImage.mViewerDirection);
				}
				if (mLoadOriginalMedia && mImage.mOriginalContentType.toLowerCase().startsWith("video/")) {
					mVideoLocation = mImageFile.getAbsolutePath();
					initMediaPlayer();
					mViewImageViewer.triggerOnImageParamChangedListener();
				} else {
					mViewImageViewer.loadImage(mImageFile.getAbsolutePath());
				}
			}
			mImageLoader = null;
			applyInfoVisibility(false);
		}
	}
}
