package com.soreepeong.darknova.ui.viewholder;

import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.soreepeong.darknova.R;
import com.soreepeong.darknova.twitter.ObjectWithId;
import com.soreepeong.darknova.ui.fragments.PageFragment;

/**
 * @author Soreepeong
 */
public abstract class CustomViewHolder<_T extends ObjectWithId> extends RecyclerView.ViewHolder implements View.OnClickListener{
	protected PageFragment<_T> mFragment;
	protected PageFragment<_T>.PageItemAdapter mAdapter;

	public CustomViewHolder(View v){
		super(v);
		v.setOnClickListener(this);
		v.setClickable(true);
		v.setFocusable(true);
		v.setTag(R.id.VIEWHOLDER_ID, this);
	}

	public void setFragment(PageFragment<_T> frag){
		mFragment = frag;
		mAdapter = frag.getAdapter();
	}

	public void updateView(){
	}

	public void releaseMemory(){
	}

	public abstract void bindViewHolder(int position);
}
