package cn.nuosi.andoroid.photogallery;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Elder on 2017/1/12.
 */

public class PhotoGalleryFragment extends Fragment {
    private static final String TAG = "PhotoGalleryFragment";

    private RecyclerView mPhotoRecyclerView;
    private List<GalleryItem> mItems = new ArrayList<>();
    private int page = 1;
    private boolean firstLoad = true;
    private PhotoAdapter mPhotoAdapter;
    private ThumbnailDownloader<PhotoHolder> mThumbnailDownloader;

    private FetchItemTask mFetchItemTask;
    private StaggeredGridLayoutManager mLayoutManager;

    public static PhotoGalleryFragment newInstance() {
        return new PhotoGalleryFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        new FetchItemTask().execute(page);

        Handler responseHandler = new Handler();
        mThumbnailDownloader = new ThumbnailDownloader<>(responseHandler);
        mThumbnailDownloader.setThumbnailDownloadListener(new ThumbnailDownloader.ThumbnailDownloadListener<PhotoHolder>() {
            @Override
            public void onThumbnailDownloaded(PhotoHolder target, Bitmap thumbnail) {
                // bitmap to change Drawable
                BitmapDrawable drawable = new BitmapDrawable(getResources(), thumbnail);
                target.bindGalleryItem(drawable);
            }
        });
        mThumbnailDownloader.start();
        // Get current thread Looper
        // 在调用getLooper（）方法之前，没办法保证onLooperPrepaerd（）方法已得到调用;
        // 调用该方法可以内部线程被等待直到Looper对象被创建好之后再被唤醒
        mThumbnailDownloader.getLooper();
        Log.i(TAG, "Background thread started");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Remove Message Queue
        mThumbnailDownloader.clearQueue();
        mThumbnailDownloader.quit();
        Log.i(TAG, "Background thread destroyed");
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.fragment_photo_gallery, container, false);

        mPhotoRecyclerView = (RecyclerView) layout
                .findViewById(R.id.fragment_photo_gallery_recycler_view);
        // 实现一个瀑布流
        mPhotoRecyclerView.setLayoutManager(new StaggeredGridLayoutManager(
                3,StaggeredGridLayoutManager.VERTICAL));

        setupAdapter();
        setupListener();
        return layout;
    }

    /**
     * RecyclerView上拉加载数据的功能
     */
    private void setupListener() {
        mPhotoRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                // 当前RecyclerView显示出来的最后一个的item的position
                int lastPosition = -1;
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {// 停止滚动时加载数据
                    StaggeredGridLayoutManager layoutManager =
                            (StaggeredGridLayoutManager) recyclerView.getLayoutManager();
                    //因为StaggeredGridLayoutManager的特殊性可能导致最后显示的item存在多个，所以这里取到的是一个数组
                    //得到这个数组后再取到数组中position值最大的那个就是最后显示的position值了
                    int[] lastPositions = new int[layoutManager.getSpanCount()];
                    lastPositions = layoutManager.findLastVisibleItemPositions(lastPositions);
                    lastPosition = findMax(lastPositions);
                    // 最后item的position是否等于itemCount总数-1也就是最后一个item的position
                    // 如果相等则说明已经滑动到最后了
                    if (lastPosition == layoutManager.getItemCount() - 1) {
                        firstLoad = false;
                        page++;
                        Toast.makeText(getActivity(), "正在加载第 " + page + " 页", Toast.LENGTH_SHORT).show();
                        new FetchItemTask().execute(page);
                    }
                }
            }
        });
    }

    /**
     * 判断最后显示Item的最大下标
     * @param lastPositions
     * @return
     */
    private int findMax(int[] lastPositions) {
        int max = lastPositions[0];
        for (int value : lastPositions) {
            if (value > max) {
                max = value;
            }
        }
        return max;
    }

    private void setupAdapter() {
        if (isAdded()) {// Return true if the fragment is currently added to its activity.
            mPhotoAdapter = new PhotoAdapter(mItems);
            mPhotoRecyclerView.setAdapter(mPhotoAdapter);
        }
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder> {
        private List<GalleryItem> mGalleryItems;
        private List<Integer> heights;

        public PhotoAdapter(List<GalleryItem> galleryItems) {
            mGalleryItems = galleryItems;
            getRandomHeight(mGalleryItems);
        }

        private void getRandomHeight(List<GalleryItem> lists){//得到随机item的高度
            heights = new ArrayList<>();
            for (int i = 0; i < lists.size(); i++) {
                heights.add((int)(200+Math.random()*400));
            }
        }

        @Override
        public PhotoHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            View view = inflater.inflate(R.layout.gallery_item, parent, false);
            return new PhotoHolder(view);
        }

        @Override
        public void onBindViewHolder(PhotoHolder holder, int position) {
            // 设置Item的随机高度
//            ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
//            layoutParams.height = heights.get(position);
//            holder.itemView.setLayoutParams(layoutParams);
            GalleryItem galleryItem = mGalleryItems.get(position);
            Drawable placeholder = ContextCompat
                    .getDrawable(getActivity(), R.drawable.bill_up_close);
            holder.bindGalleryItem(placeholder);// Load default Image
            // 异步加载真实地址的图片
            mThumbnailDownloader.queueThumbnail(holder, galleryItem.getUrl());
        }

        @Override
        public int getItemCount() {
            return mGalleryItems.size();
        }
    }


    private class PhotoHolder extends RecyclerView.ViewHolder {
        private ImageView mItemImageView;

        public PhotoHolder(View itemView) {
            super(itemView);

            mItemImageView = (ImageView) itemView
                    .findViewById(R.id.fragment_photo_gallery_image_view);

        }

        public void bindGalleryItem(Drawable drawable) {
            mItemImageView.setImageDrawable(drawable);
        }

    }

    private class FetchItemTask extends AsyncTask<Integer,Void,List<GalleryItem>> {

        @Override
        protected List<GalleryItem> doInBackground(Integer... params) {
            Integer page = params[0];
            return new FlickrFetchr().fetchItems(page);
        }

        @Override
        protected void onPostExecute(List<GalleryItem> galleryItems) {// 该方法在主线程运行
            if (firstLoad) {
                mItems = galleryItems;
                setupAdapter();
                return;
            } else {
                mItems.addAll(galleryItems);
                mPhotoAdapter.notifyDataSetChanged();
            }
        }
    }
}
