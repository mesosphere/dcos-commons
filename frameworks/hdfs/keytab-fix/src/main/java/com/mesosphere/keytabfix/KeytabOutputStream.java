/**
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *  
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */
package com.mesosphere.keytabfix;

import org.apache.kerby.kerberos.kerb.KrbOutputStream;
import org.apache.kerby.kerberos.kerb.type.base.EncryptionKey;
import org.apache.kerby.kerberos.kerb.type.base.PrincipalName;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class KeytabOutputStream extends KrbOutputStream {
    public KeytabOutputStream(OutputStream out) {
        super(out);
    }

    public void writePrincipal(PrincipalName principal, int version) throws IOException {
        List<String> nameStrings = principal.getNameStrings();
        int numComponents = principal.getNameStrings().size();
        String realm = principal.getRealm();

        writeShort(numComponents);

        writeCountedString(realm);

        for (String nameCom : nameStrings) {
        	if(nameCom == null) {
        		writeCountedString("null");
        	} else {
        		writeCountedString(nameCom);
        	}
        }

        writeInt(principal.getNameType().getValue()); // todo: consider the version
    }

    @Override
    public void writeKey(EncryptionKey key, int version) throws IOException {
        writeShort(key.getKeyType().getValue());
        writeCountedOctets(key.getKeyData());
    }

    @Override
    public void writeCountedOctets(byte[] data) throws IOException {
        writeShort(data.length);
        write(data);
    }
}
