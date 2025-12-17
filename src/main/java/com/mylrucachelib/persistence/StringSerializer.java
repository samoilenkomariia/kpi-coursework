package com.mylrucachelib.persistence;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class StringSerializer implements Serializer<String> {
    @Override
    public void serialize(DataOutputStream out, String object) throws IOException {
        out.writeUTF(object); // built in string encoding
    }

    @Override
    public String deserialize(DataInputStream in) throws IOException {
        return in.readUTF();
    }
}
