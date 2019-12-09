package main

import "testing"
import "github.com/stretchr/testify/assert"

func TestFirstArgumentIndex(t *testing.T) {

	assert.Equal(t, firstArgumentIndex("hdfs", []string{"hdfs", "--flag"}), 0)
	assert.Equal(t, firstArgumentIndex("hdfs", []string{"--flag", "hdfs"}), 1)
	assert.Equal(t, firstArgumentIndex("hdfs", []string{"-f", "hdfs"}), 1)
	assert.Equal(t, firstArgumentIndex("hdfs", []string{"--flag", "argument", "hdfs"}), -1)
	assert.Equal(t, firstArgumentIndex("hdfs", []string{"--flag", "hdfs", "argument"}), 1)
	assert.Equal(t, firstArgumentIndex("hdfs", []string{}), -1)
}
