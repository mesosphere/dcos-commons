package main

import (
  "bufio"
  "bytes"
  "io"
  "os"
  "strings"
)

/**
 * Parses the given principals file to an array of principals
 */
func ParsePrincipalsFile(filename string) ([]KPrincipal, error) {
  file, err := os.Open(filename)
  if err != nil {
    return nil, err
  }
  defer file.Close()

  return ParsePrincipalsFrom(file)
}

/**
 * Parses the given principals string to an array of principals
 */
func ParsePrincipalsBytes(contents []byte) ([]KPrincipal, error) {
  return ParsePrincipalsFrom(bytes.NewReader(contents))
}

/**
 * Parses the given principals string to an array of principals
 */
func ParsePrincipalsFrom(contents io.Reader) ([]KPrincipal, error) {
  var principals []KPrincipal
  scanner := bufio.NewScanner(contents)
  for scanner.Scan() {
    p, err := ParsePrincipal(strings.Trim(scanner.Text(), " \t"))
    if err != nil {
      return nil, err
    }
    principals = append(principals, p)
  }
  return principals, scanner.Err()
}

/**
 * Parses principals from KAdmin `list --long` output
 */
func ParsePrincipalsKadminLong(contents string) ([]KPrincipal, error) {
  var principals []KPrincipal

  scanner := bufio.NewScanner(strings.NewReader(contents))
  for scanner.Scan() {
    line := strings.Trim(scanner.Text(), " \t")
    if strings.HasPrefix(line, "Principal:") {
      p, err := ParsePrincipal(line[11:])
      if err != nil {
        return nil, err
      }
      principals = append(principals, p)
    }

  }
  return principals, scanner.Err()
}
