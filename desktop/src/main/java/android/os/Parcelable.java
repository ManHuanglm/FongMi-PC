package android.os;

/**
 * 桌面端 android.os.Parcelable 编译桩（桌面端永不执行序列化，仅满足挂载代码编译）
 */
public interface Parcelable {

    int describeContents();

    void writeToParcel(Parcel dest, int flags);

    interface Creator<T> {

        T createFromParcel(Parcel source);

        T[] newArray(int size);
    }
}
