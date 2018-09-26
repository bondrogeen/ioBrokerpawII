package ru.codedevice.iobrokerpawii;

import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class RecycleListAdapter extends RecyclerView.Adapter<RecycleListAdapter.ExampleViewHolder>{

    private ArrayList<RecycleItem> mExampleList;
    private OnItemClickListener mListener;

    public interface OnItemClickListener{
        void onItemSingleClick(int position);
        void onItemDoubleClick(int position);
        void onItemLongClick(int position);
    }

    public void setOnItemClickListener(OnItemClickListener listener){
        mListener = listener;
    }

    public static class ExampleViewHolder extends RecyclerView.ViewHolder {
        public ImageView mImage;
        public TextView mTitle;
        private TextView mTopic;
        private CardView mCardView;

        public ExampleViewHolder(View itemView , final OnItemClickListener listener) {
            super(itemView);
            mImage = itemView.findViewById(R.id.buttonImage);
            mTitle = itemView.findViewById(R.id.buttonTitle);
            mTopic = itemView.findViewById(R.id.buttonTopic);
            mCardView = itemView.findViewById(R.id.buttonCardView);

            itemView.setOnClickListener(new DoubleClickListener() {

                @Override
                public void onSingleClick(View v) {
                    if(listener !=null){
                        int position = getAdapterPosition();
                        if(position != RecyclerView.NO_POSITION){
                            listener.onItemSingleClick(position);
                        }

                    }
                }

                @Override
                public void onDoubleClick(View v) {
                    if(listener !=null){
                        int position = getAdapterPosition();
                        if(position != RecyclerView.NO_POSITION){
                            listener.onItemDoubleClick(position);
                        }

                    }
                }
            });

            itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if(listener !=null){
                        int position = getAdapterPosition();
                        if(position != RecyclerView.NO_POSITION){
                            listener.onItemLongClick(position);
                        }

                    }
                    return true;
                }
            });



        }

    }

    public RecycleListAdapter(ArrayList<RecycleItem> exampleList){
        mExampleList = exampleList;
    }

    @NonNull
    @Override
    public ExampleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int i) {
        LayoutInflater inflater  = LayoutInflater.from(parent.getContext());
        View v = inflater .inflate(R.layout.layout_item_button,parent,false);
        ExampleViewHolder evh = new ExampleViewHolder(v,mListener);
        return evh;
    }

    @Override
    public void onBindViewHolder(@NonNull ExampleViewHolder holder, int position) {
        RecycleItem currentItem = mExampleList.get(position);

        holder.mImage.setImageResource(currentItem.getImage());
        holder.mTitle.setText(currentItem.getTitle());
        holder.mTopic.setText(currentItem.getTopic());
//        holder.mCardView.setAlpha((float) 0.3);
    }

    @Override
    public int getItemCount() {
        return mExampleList.size();
    }





    public abstract static class DoubleClickListener implements View.OnClickListener {

        private Timer timer = null;
        private int DELAY   = 250;
        private static final long DOUBLE_CLICK_TIME_DELTA = 200;
        long lastClickTime = 0;

        @Override
        public void onClick(View v) {
            long clickTime = System.currentTimeMillis();
            if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA){
                processDoubleClickEvent(v);
            } else {
                processSingleClickEvent(v);
            }
            lastClickTime = clickTime;
        }

        public void processSingleClickEvent(final View v){

            final Handler handler=new Handler();
            final Runnable mRunnable=new Runnable(){
                public void run(){
                    onSingleClick(v); //Do what ever u want on single click

                }
            };

            TimerTask timertask=new TimerTask(){
                @Override
                public void run(){
                    handler.post(mRunnable);
                }
            };
            timer=new Timer();
            timer.schedule(timertask,DELAY);

        }

        public void processDoubleClickEvent(View v){
            if(timer!=null)
            {
                timer.cancel();
                timer.purge();
            }
            onDoubleClick(v);
        }

        public abstract void onSingleClick(View v);

        public abstract void onDoubleClick(View v);
    }

}
