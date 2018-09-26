package ru.codedevice.iobrokerpawii;

public class RecycleItem {
    private int mImage;
    private String mTitle;
    private String mTopic;
    private String mType;

    public RecycleItem(int imageResource, String title, String topic,String type){
        mImage = imageResource;
        mTitle = title;
        mTopic = topic;
        mType = type;
    }

    public void changeTitle(String title){
        mTitle = title;
    }
    public void changeTopic(String topic) { mTitle = topic; }
    public void changeType(String type){ mTopic = type; }

    public int getImage() {
        return mImage;
    }

    public String getTitle(){
        return mTitle;
    }
    public String getTopic(){
        return mTopic;
    }
    public String getType(){ return mTopic; }
}
