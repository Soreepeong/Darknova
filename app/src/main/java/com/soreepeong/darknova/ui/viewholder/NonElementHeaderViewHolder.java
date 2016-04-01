package com.soreepeong.darknova.ui.viewholder;

import android.view.View;

import com.soreepeong.darknova.R;
import com.soreepeong.darknova.ui.MainActivity;
import com.soreepeong.darknova.ui.fragments.PageFragment;

/**
 * @author Soreepeong
 */
public class NonElementHeaderViewHolder extends CustomViewHolder{
	public NonElementHeaderViewHolder(View itemView){
		super(itemView);
	}

	@Override
	public void onClick(View v){
		PageFragment.NonElementHeader header = (PageFragment.NonElementHeader) mAdapter.getItem(getAdapterPosition());
		switch(header.getLayoutId()){
			case R.layout.row_header_empty_page:
				mFragment.onRefresh();
				return;
			case R.layout.row_header_no_match:
				((MainActivity) mFragment.getActivity()).getSearchSuggestionFragment().setQuickFilter("");
		}
	}

	@Override
	public void bindViewHolder(int position){

	}
}
