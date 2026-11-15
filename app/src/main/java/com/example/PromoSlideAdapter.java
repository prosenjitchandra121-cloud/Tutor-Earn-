package com.example;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.databinding.ItemPromoSlideBinding;

/**
 * RecyclerView adapter backing the infinite-looping 3-slide promotional banner carousel.
 */
public class PromoSlideAdapter extends RecyclerView.Adapter<PromoSlideAdapter.SlideViewHolder> {

    public static final int SLIDE_COUNT = 3;
    public static final int VIRTUAL_COUNT = 3000;
    public static final int START_POSITION = 1500;

    private static final class SlideData {
        @StringRes
        final int titleRes;
        @StringRes
        final int subtitleRes;
        @DrawableRes
        final int imageRes;
        final String highlightPhrase;

        SlideData(@StringRes int titleRes, @StringRes int subtitleRes, @DrawableRes int imageRes, String highlightPhrase) {
            this.titleRes = titleRes;
            this.subtitleRes = subtitleRes;
            this.imageRes = imageRes;
            this.highlightPhrase = highlightPhrase;
        }
    }

    private final SlideData[] slides = new SlideData[]{
            new SlideData(
                    R.string.promo_slide_1_title,
                    R.string.promo_slide_1_subtitle,
                    R.drawable.banner_slide_1,
                    "Earn Smarter!"
            ),
            new SlideData(
                    R.string.promo_slide_2_title,
                    R.string.promo_slide_2_subtitle,
                    R.drawable.banner_slide_2,
                    "Your Rewards!"
            ),
            new SlideData(
                    R.string.promo_slide_3_title,
                    R.string.promo_slide_3_subtitle,
                    R.drawable.banner_slide_3,
                    "Power of AI!"
            )
    };

    @NonNull
    @Override
    public SlideViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPromoSlideBinding binding = ItemPromoSlideBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new SlideViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SlideViewHolder holder, int position) {
        int actualIndex = ((position % SLIDE_COUNT) + SLIDE_COUNT) % SLIDE_COUNT;
        holder.bind(slides[actualIndex]);
    }

    @Override
    public int getItemCount() {
        return VIRTUAL_COUNT;
    }

    static class SlideViewHolder extends RecyclerView.ViewHolder {
        private final ItemPromoSlideBinding binding;

        SlideViewHolder(@NonNull ItemPromoSlideBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(SlideData data) {
            Context context = binding.getRoot().getContext();
            String title = context.getString(data.titleRes);
            SpannableString styledTitle = new SpannableString(title);
            int highlightIndex = title.indexOf(data.highlightPhrase);
            if (highlightIndex >= 0) {
                int goldColor = ContextCompat.getColor(context, R.color.gold_primary);
                styledTitle.setSpan(
                        new ForegroundColorSpan(goldColor),
                        highlightIndex,
                        highlightIndex + data.highlightPhrase.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
            binding.promoSlideTitle.setText(styledTitle);
            binding.promoSlideSubtitle.setText(data.subtitleRes);
            binding.promoSlideImage.setImageResource(data.imageRes);
            binding.promoSlideImage.setContentDescription(title);
        }
    }
}
