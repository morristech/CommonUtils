package com.gianlu.commonutils;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class InfiniteRecyclerView extends RecyclerView {
    private IFailedLoadingContent listener;

    public InfiniteRecyclerView(Context context) {
        super(context);
        addOnScrollListener(new CustomScrollListener());
    }

    public InfiniteRecyclerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        addOnScrollListener(new CustomScrollListener());
    }

    public InfiniteRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        addOnScrollListener(new CustomScrollListener());
    }

    public void setFailedListener(IFailedLoadingContent listener) {
        this.listener = listener;

        InfiniteAdapter adapter = (InfiniteAdapter) getAdapter();
        if (getAdapter() != null)
            adapter.attachListener(listener);
    }

    public void setAdapter(InfiniteAdapter adapter) {
        super.setAdapter(adapter);

        if (listener != null)
            adapter.attachListener(listener);
    }

    public interface IFailedLoadingContent {
        void onFailedLoadingContent(Exception ex);
    }

    public static abstract class InfiniteAdapter<VH extends ViewHolder, E> extends RecyclerView.Adapter<ViewHolder> {
        protected static final int ONE_PAGE = -2;
        protected static final int UNDETERMINED_PAGES = -1;
        static final int ITEM_LOADING = 0;
        static final int ITEM_NORMAL = 1;
        static final int ITEM_SEPARATOR = 2;
        protected final LayoutInflater inflater;
        protected final List<ItemEnclosure<E>> items;
        private final Handler handler;
        private final Config config;
        int page = 1;
        long currDay = -1;
        private IFailedLoadingContent listener;
        private boolean loading = false;

        public InfiniteAdapter(Config<E> config) {
            this.config = config;
            this.inflater = LayoutInflater.from(config.context);
            this.handler = new Handler(Looper.getMainLooper());
            this.items = new ArrayList<>();

            populate(config.items);
        }

        @NonNull
        protected Context getContext() {
            return config.context;
        }

        protected int remove(E element) {
            int pos = indexOf(element);
            if (pos != -1) items.remove(pos);
            return pos;
        }

        protected int indexOf(E element) {
            for (int i = 0; i < items.size(); i++)
                if (Objects.equals(element, items.get(i).item))
                    return i;

            return -1;
        }

        @Override
        public final int getItemCount() {
            return items.size();
        }

        private void populate(List<E> elements) {
            for (E element : elements) {
                Date date = getDateFromItem(element);
                if (config.separator != null && date != null && currDay != date.getTime() / 86400000) {
                    items.add(new ItemEnclosure<E>(null, date));
                    currDay = date.getTime() / 86400000;
                }

                items.add(new ItemEnclosure<>(element, date));
            }
        }

        @Nullable
        protected abstract Date getDateFromItem(E item);

        private void attachListener(IFailedLoadingContent listener) {
            this.listener = listener;
        }

        @Override
        public final int getItemViewType(int position) {
            if (items.get(position) == null) return ITEM_LOADING;
            else if (items.get(position).item == null) return ITEM_SEPARATOR;
            else return ITEM_NORMAL;
        }

        @NonNull
        @Override
        public final ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == ITEM_LOADING)
                return new LoadingViewHolder(inflater.inflate(R.layout.loading_item, parent, false));
            else if (viewType == ITEM_SEPARATOR)
                return new SeparatorViewHolder(inflater.inflate(R.layout.separator_item, parent, false));
            else
                return createViewHolder(parent);
        }

        private int indexOfSeparator(Date date) {
            for (int i = 0; i < items.size(); i++) {
                ItemEnclosure item = items.get(i);
                if (item.date == date && item.item == null) return i;
            }

            return -1;
        }

        private int countFor(Date date) {
            int pos = indexOfSeparator(date);
            if (pos == -1) return 0;

            int count = 0;
            for (int i = pos + 1; i < items.size(); i++) {
                if (items.get(i).item == null) break;
                else count++;
            }

            return count;
        }

        @Override
        @SuppressWarnings("unchecked")
        public final void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ItemEnclosure<E> item = items.get(position);
            if (item != null) {
                if (item.item == null && item.date != null && config.separator != null) {
                    SeparatorViewHolder separator = (SeparatorViewHolder) holder;
                    separator.line.setBackground(position == 0 ? null : config.separator);
                    if (config.separatorWithCount)
                        separator.date.setText(String.format(Locale.getDefault(), "%s (%d)", CommonUtils.getVerbalDateFormatter().format(item.date), countFor(item.date)));
                    else
                        separator.date.setText(CommonUtils.getVerbalDateFormatter().format(item.date));
                } else if (item.item != null) {
                    userBindViewHolder((VH) holder, item, position);
                }
            }
        }

        protected final void maxPages(int max) {
            config.maxPages = max;
        }

        protected abstract void userBindViewHolder(@NonNull VH holder, @NonNull ItemEnclosure<E> item, int position);

        protected abstract ViewHolder createViewHolder(ViewGroup parent);

        private int findLastSeparator() {
            for (int i = items.size() - 1; i >= 0; i--) {
                ItemEnclosure item = items.get(i);
                if (item != null && item.item == null) return i;
            }

            return -1;
        }

        private void loadMoreContent() {
            if (config.maxPages == ONE_PAGE || loading) return;
            if (config.maxPages != UNDETERMINED_PAGES && page > config.maxPages) return;

            loading = true;

            if (items.get(items.size() - 1) != null) {
                items.add(null);
                notifyItemInserted(items.size() - 1);
            }

            page++;
            moreContent(page, new IContentProvider<E>() {
                @Override
                public void onMoreContent(final List<E> content) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (items.get(items.size() - 1) == null) {
                                items.remove(null);
                                notifyItemRemoved(items.size() - 1);
                            }

                            int start = items.size();
                            int lastSeparator = findLastSeparator();
                            populate(content);
                            if (config.separatorWithCount && lastSeparator != -1)
                                notifyItemChanged(lastSeparator);
                            notifyItemRangeInserted(start, content.size());
                            loading = false;
                        }
                    });
                }

                @Override
                public void onReloadAllContent(final List<E> content) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (items.get(items.size() - 1) == null) {
                                items.remove(null);
                                notifyItemRemoved(items.size() - 1);
                            }

                            items.clear();
                            populate(content);
                            notifyDataSetChanged();
                            loading = false;
                        }
                    });
                }

                @Override
                public void onFailed(Exception ex) {
                    if (listener != null && config.maxPages != UNDETERMINED_PAGES)
                        listener.onFailedLoadingContent(ex);
                    Logging.log(ex);

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (items.get(items.size() - 1) == null) {
                                items.remove(null);
                                notifyItemRemoved(items.size() - 1);
                            }

                            loading = false;
                        }
                    });
                }
            });
        }

        protected abstract void moreContent(int page, IContentProvider<E> provider);

        protected interface IContentProvider<E> {
            void onMoreContent(List<E> content);

            void onReloadAllContent(List<E> content);

            void onFailed(Exception ex);
        }

        public final static class Config<E> {
            private final Context context;
            private final List<E> items;
            private int maxPages = UNDETERMINED_PAGES;
            private Drawable separator = null;
            private boolean separatorWithCount = false;

            public Config(Context context) {
                this.context = context;
                this.items = new ArrayList<>();
            }

            public Config<E> items(Collection<? extends E> items) {
                this.items.addAll(items);
                return this;
            }

            public Config<E> maxPages(int max) {
                maxPages = max;
                return this;
            }

            public Config<E> onePage() {
                maxPages = ONE_PAGE;
                return this;
            }

            public Config<E> undeterminedPages() {
                maxPages = UNDETERMINED_PAGES;
                return this;
            }

            public Config<E> noSeparators() {
                separator = null;
                separatorWithCount = false;
                return this;
            }

            public Config<E> separators(boolean withCount) {
                separatorWithCount = withCount;
                TypedArray a = context.obtainStyledAttributes(new int[]{android.R.attr.listDivider});
                separator = a.getDrawable(0);
                a.recycle();
                return this;
            }
        }

        public static class ItemEnclosure<E> {
            final Date date;
            final E item;

            public ItemEnclosure(@Nullable E item, @Nullable Date date) {
                if (item == null && date == null)
                    throw new NullPointerException("item and date cannot be both null!");

                this.date = date;
                this.item = item;
            }

            public E getItem() {
                return item;
            }
        }

        private class LoadingViewHolder extends ViewHolder {
            LoadingViewHolder(View itemView) {
                super(itemView);
            }
        }

        private class SeparatorViewHolder extends ViewHolder {
            final TextView date;
            final View line;

            SeparatorViewHolder(View itemView) {
                super(itemView);

                line = itemView.findViewById(R.id.separatorItem_line);
                date = itemView.findViewById(R.id.separatorItem_date);
            }
        }
    }

    private class CustomScrollListener extends RecyclerView.OnScrollListener {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, final int dy) {
            recyclerView.post(new Runnable() {
                @Override
                public void run() {
                    if (!canScrollVertically(1) && dy > 0)
                        if (getAdapter() instanceof InfiniteAdapter)
                            ((InfiniteAdapter) getAdapter()).loadMoreContent();
                }
            });
        }
    }
}
