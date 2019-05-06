package main

import (
  "bufio"
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

  var principals []KPrincipal
  scanner := bufio.NewScanner(file)
  for scanner.Scan() {
    p, err := ParsePrincipal(scanner.Text())
    if err != nil {
      return nil, err
    }
    principals = append(principals, p)
  }
  return principals, scanner.Err()
}

/**
 * Parses the given principals string to an array of principals
 */
func ParsePrincipals(contents string) ([]KPrincipal, error) {
  var principals []KPrincipal
  scanner := bufio.NewScanner(strings.NewReader(contents))
  for scanner.Scan() {
    p, err := ParsePrincipal(scanner.Text())
    if err != nil {
      return nil, err
    }
    principals = append(principals, p)
  }
  return principals, scanner.Err()
}
