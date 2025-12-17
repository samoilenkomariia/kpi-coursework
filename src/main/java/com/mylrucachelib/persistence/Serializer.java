package com.mylrucachelib.persistence;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public interface Serializer<T> {
    void serialize(DataOutputStream out, T objects) throws IOException;
    T deserialize(DataInputStream in) throws IOException;
}
