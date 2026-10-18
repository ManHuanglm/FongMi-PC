package android.os;

import java.util.ArrayList;
import java.util.List;

/**
 * 桌面端 android.os.Parcel 编译桩（桌面端永不执行序列化，读写均为空操作）
 */
public class Parcel {

    private Parcel() {
    }

    public void writeInt(int value) {
    }

    public void writeLong(long value) {
    }

    public void writeFloat(float value) {
    }

    public void writeDouble(double value) {
    }

    public void writeByte(byte value) {
    }

    public void writeString(String value) {
    }

    public void writeValue(Object value) {
    }

    public void writeParcelable(Parcelable value, int flags) {
    }

    public <T extends Parcelable> void writeTypedList(List<T> value) {
    }

    public void writeStringList(List<String> value) {
    }

    public void writeList(List value) {
    }

    public int readInt() {
        return 0;
    }

    public long readLong() {
        return 0;
    }

    public float readFloat() {
        return 0;
    }

    public double readDouble() {
        return 0;
    }

    public byte readByte() {
        return 0;
    }

    public String readString() {
        return null;
    }

    public Object readValue(ClassLoader loader) {
        return null;
    }

    public <T extends Parcelable> T readParcelable(ClassLoader loader) {
        return null;
    }

    public <T extends Parcelable> ArrayList<T> createTypedArrayList(Parcelable.Creator<T> creator) {
        return new ArrayList<>();
    }

    public ArrayList<String> createStringArrayList() {
        return new ArrayList<>();
    }

    public <T> void readList(List<T> outVal, ClassLoader loader) {
    }
}
