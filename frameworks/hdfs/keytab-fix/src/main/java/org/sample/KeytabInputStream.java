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
package org.sample;

import org.apache.kerby.kerberos.kerb.type.KerberosTime;
import org.apache.kerby.kerberos.kerb.type.base.NameType;
import org.apache.kerby.kerberos.kerb.type.base.PrincipalName;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class KeytabInputStream extends KrbInputStream {
    public KeytabInputStream(InputStream in) {
        super(in);
    }

    public KerberosTime readTime() throws IOException {
        long value = readInt();
        KerberosTime time = new KerberosTime(value * 1000);
        return time;
    }

    @Override
    public PrincipalName readPrincipal(int version) throws IOException {
        int numComponents = readShort();
        if (version == Keytab.V501) {
            numComponents -= 1;
        }

        String realm = readCountedString();
        List<String> nameStrings = new ArrayList<String>();
        for (int i = 0; i < numComponents; i++) { // sub 1 if version 0x501
            String component = readCountedString();
            nameStrings.add(component);
        }
        int type = readInt(); // not present if version 0x501
        NameType nameType = NameType.fromValue(type);
        PrincipalName principal = new PrincipalName(nameStrings, nameType);
        principal.setRealm(realm);

        return principal;
    }

    @Override
    public int readOctetsCount() throws IOException {
        return readShort();
    }
}
