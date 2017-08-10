package main

import (
	"bytes"
	"encoding/base64"
	"io/ioutil"
	"os"
	"path/filepath"

	"testing"
)

func TestSecretDecoder(t *testing.T) {
	dir, err := ioutil.TempDir("", "keystore-test")
	if err != nil {
		t.Fatal("Failed to create temp directory err: ", err)
	}
	defer os.RemoveAll(dir)

	// Write various base64 encoded files with different suffixes
	content := []byte("!@#$%")
	for _, suffix := range []string{"keystore", "truststore", "invalid"} {
		writeBase64EncodedData(
			filepath.Join(dir, "test."+suffix+".base64"),
			content)
	}

	// Run secret decoder
	decoder := NewSecretBase64Decoder(dir)
	decoder.decodeKeystores()

	// Check that expected files got decoded
	for _, suffix := range []string{"keystore", "truststore"} {
		path := filepath.Join(dir, "test."+suffix)
		decoded, err := ioutil.ReadFile(path)

		if err != nil {
			t.Fatal("Failed to read '", path, "' file : ", err)
		}

		if !bytes.Equal(content, decoded) {
			t.Fatal("Decoded file content '", path, "' isn't matching",
				"expected content: ", string(decoded), "vs", string(content))
		}
	}

	// Check that file without matching suffix isn't decoded
	_, err = os.Stat(filepath.Join(dir, "test.invalid"))
	if !os.IsNotExist(err) {
		t.Fatal("File test.invalid exists, shouldn't get decoded")
	}
}

func writeBase64EncodedData(dstPath string, data []byte) error {
	return ioutil.WriteFile(
		dstPath,
		[]byte(
			base64.StdEncoding.EncodeToString(
				data)),
		0644)
}
