package main

import (
	"bytes"
	"encoding/base64"
	"io/ioutil"
	"log"
	"os"
	"path/filepath"
	"strings"
)

// secretsBase64Decoder finds all files matching *.keystore.base64 and
// *.truststore.base64 name pattern and decodes files from BASE64 encoded
// files to binary files. Files are stored in the same location without
// base64 suffix.
//
// This is a workaround for secret store not supporting binary files and
// should be removed once DCOS-16005 is addressed (probably in DCOS 1.11)
type secretsBase64Decoder struct {
	path string
}

func NewSecretBase64Decoder(path string) *secretsBase64Decoder {
	return &secretsBase64Decoder{
		path: path,
	}
}

func (d *secretsBase64Decoder) decodeKeystores() error {
	matches, err := filepath.Glob(d.path + "/*.keystore.base64")
	if err != nil {
		return err
	}

	truststoreMatches, err := filepath.Glob(
		d.path + "/*.truststore.base64")
	if err != nil {
		return err
	}

	matches = append(matches, truststoreMatches...)
	if len(matches) == 0 {
		log.Println("No keystore files found to be decoded")
	} else {
		log.Printf("Decoding '%d' keystore files from Base64\n", len(matches))
	}

	// Decode each file
	for _, path := range matches {
		log.Printf("Processing file: %s\n", path)
		srcFileStat, err := os.Stat(path)
		if err != nil {
			return err
		}

		srcData, err := ioutil.ReadFile(path)
		if err != nil {
			return err
		}

		dstData := make([]byte, base64.StdEncoding.EncodedLen(len(srcData)))
		base64.StdEncoding.Decode(dstData, srcData)
		// Remove any trailing zero bytes
		dstData = bytes.TrimRight(dstData, "\x00")

		dstPath := strings.TrimSuffix(path, ".base64")
		// WriteFile truncates file before writing if file exists so there will
		// be always fresh data
		ioutil.WriteFile(dstPath, dstData, srcFileStat.Mode())
		log.Printf("Decoded BASE64 file '%s' -> '%s'\n", path, dstPath)
	}

	return nil
}
