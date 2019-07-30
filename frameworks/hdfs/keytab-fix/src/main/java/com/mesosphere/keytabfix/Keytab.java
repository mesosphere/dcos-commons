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

import org.apache.kerby.kerberos.kerb.type.base.EncryptionKey;
import org.apache.kerby.kerberos.kerb.type.base.EncryptionType;
import org.apache.kerby.kerberos.kerb.type.base.PrincipalName;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Keytab management util.
 */
public final class Keytab implements KrbKeytab {

    public static final int V501 = 0x0501;
    public static final int V502 = 0x0502;

    private int version = V502;

    private Map<PrincipalName, List<KeytabEntry>> principalEntries;

    public Keytab() {
        this.principalEntries = new HashMap<PrincipalName, List<KeytabEntry>>();
    }

    public static Keytab loadKeytab(File keytabFile) throws IOException {
        Keytab keytab = new Keytab();
        keytab.load(keytabFile);
        return keytab;
    }

    public static Keytab loadKeytab(InputStream inputStream) throws IOException {
        Keytab keytab = new Keytab();
        keytab.load(inputStream);
        return keytab;
    }

    @Override
    public List<PrincipalName> getPrincipals() {
        return new ArrayList<PrincipalName>(principalEntries.keySet());
    }

    @Override
    public void addKeytabEntries(List<KeytabEntry> entries) {
        for (KeytabEntry entry : entries) {
            addEntry(entry);
        }
    }

    @Override
    public void removeKeytabEntries(PrincipalName principal) {
        principalEntries.remove(principal);
    }

    @Override
    public void removeKeytabEntries(PrincipalName principal, int kvno) {
        List<KeytabEntry> entries = getKeytabEntries(principal);
        for (KeytabEntry entry : entries) {
            if (entry.getKvno() == kvno) {
                removeKeytabEntry(entry);
            }
        }
    }

    @Override
    public void removeKeytabEntry(KeytabEntry entry) {
        PrincipalName principal = entry.getPrincipal();
        List<KeytabEntry> entries = principalEntries.get(principal);
        if (entries != null) {
            Iterator<KeytabEntry> iter = entries.iterator();
            while (iter.hasNext()) {
                KeytabEntry tmp = iter.next();
                if (entry.equals(tmp)) {
                    iter.remove();
                    break;
                }
            }
        }
    }

    @Override
    public List<KeytabEntry> getKeytabEntries(PrincipalName principal) {
        List<KeytabEntry> results = new ArrayList<KeytabEntry>();

        List<KeytabEntry> internal = principalEntries.get(principal);
        if (internal == null) {
            return results;
        }

        for (KeytabEntry entry : internal) {
            results.add(entry);
        }

        return results;
    }

    @Override
    public EncryptionKey getKey(PrincipalName principal, EncryptionType keyType) {
        List<KeytabEntry> entries = getKeytabEntries(principal);
        for (KeytabEntry ke : entries) {
            if (ke.getKey().getKeyType() == keyType) {
                return ke.getKey();
            }
        }

        return null;
    }

    @Override
    public void load(File keytabFile) throws IOException {
    	
    	InputStream is = null;
        if (!keytabFile.exists() || !keytabFile.canRead()) {
            throw new IllegalArgumentException("Invalid keytab file: " + keytabFile.getAbsolutePath());
        }
        
        try {
        	is = Files.newInputStream(keytabFile.toPath());
            load(is);
        }
        finally {
        	if(is != null) {
        		is.close(); 	
        	}        	
        }
    }

    @Override
    public void load(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("Invalid and null input stream");
        }

        KeytabInputStream kis = new KeytabInputStream(inputStream);
        doLoad(kis);
    }

    private void doLoad(KeytabInputStream kis) throws IOException {
        this.version = readVersion(kis);

        List<KeytabEntry> entries = readEntries(kis);
        addKeytabEntries(entries);
        
    }

    @Override
    public void addEntry(KeytabEntry entry) {
        PrincipalName principal = entry.getPrincipal();
        List<KeytabEntry> entries = principalEntries.get(principal);
        if (entries == null) {
            entries = new ArrayList<KeytabEntry>();
            principalEntries.put(principal, entries);
        }
        entries.add(entry);
    }

    private int readVersion(KeytabInputStream kis) throws IOException {
        return kis.readShort();
    }

    private List<KeytabEntry> readEntries(KeytabInputStream kis) throws IOException {
        List<KeytabEntry> entries = new ArrayList<KeytabEntry>();

        int bytesLeft = kis.available();
        while (bytesLeft > 0) {
            int entrySize = kis.readInt();
            if (kis.available() < entrySize) {
                throw new IOException("Bad input stream with less data than expected: " + entrySize);
            }
            KeytabEntry entry = readEntry(kis, entrySize);
            entries.add(entry);
            int bytesReadForEntry = bytesLeft - kis.available() - 4;
            if (bytesReadForEntry != entrySize) {
                kis.skipBytes(entrySize - bytesReadForEntry); // reposition to the next keytab entry
            }
            //kis.readInt(); //TODO: This is a tricky fix, actual fix will be correct the bytesReadForEntry
            // calculation. It should not consider first 4 byte specifying size as read bytes for entry.
            bytesLeft = kis.available();
        }

        return entries;
    }

    private KeytabEntry readEntry(KeytabInputStream kis, int entrySize) throws IOException {
        KeytabEntry entry = new KeytabEntry();
        entry.load(kis, version, entrySize);
        return entry;
    }

    @Override
    public void store(File keytabFile) throws IOException {
        try (OutputStream outputStream = Files.newOutputStream(keytabFile.toPath())) {
            store(outputStream);
        }
    }

    @Override
    public void store(OutputStream outputStream) throws IOException {
        if (outputStream == null) {
            throw new IllegalArgumentException("Invalid and null output stream");
        }

        KeytabOutputStream kos = new KeytabOutputStream(outputStream);

        writeVersion(kos);
        writeEntries(kos);
    }

    private void writeVersion(KeytabOutputStream kos) throws IOException {
        byte[] bytes = new byte[2];
        bytes[0] = (byte) 0x05;
        bytes[1] = version == V502 ? (byte) 0x02 : (byte) 0x01;

        kos.write(bytes);
    }

    private void writeEntries(KeytabOutputStream kos) throws IOException {
        for (Map.Entry<PrincipalName, List<KeytabEntry>> entryList : principalEntries.entrySet()) {
            for (KeytabEntry entry : entryList.getValue()) {
                entry.store(kos);
            }
        }
    }

}
