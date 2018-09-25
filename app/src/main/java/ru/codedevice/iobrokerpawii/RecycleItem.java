package ru.codedevice.iobrokerpawii;

public class RecycleItem {
    private int mImageResource;
    private String mText1;
    private String mText2;

    public RecycleItem(int imageResource, String text1, String text2){
        mImageResource = imageResource;
        mText1 = text1;
        mText2 = text2;
    }


    public void changeText1(String text){
        mText1 = text;
    }


    public int getmImageResource() {
        return mImageResource;
    }

    public String getText1(){
        return mText1;
    }

    public String getText2(){
        return mText2;
    }
}
