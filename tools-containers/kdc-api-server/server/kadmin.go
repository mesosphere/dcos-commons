package main

import (
  "bytes"
  "encoding/json"
  "fmt"
  "io"
  "io/ioutil"
  "os"
  "os/exec"
  "regexp"
  "strings"
  "syscall"
)

/**
 * Constants for the CLI syntax to use
 */
const API_HL5 = "heimdal" // Heimdal (HL5) syntax for the CLI commands
const API_MIT = "mit"     // MIT Kerberos (KRB5) syntax for the CLI commands

/**
 * Kadmin interface
 */
type KAdminClient struct {
  KAdminPath      string
  KAdminApiFlavor string
}

/**
 * An error thrown by the KAdminClient.exec on execution errors
 */
type KExecError struct {
  ExitCode int
  Stderr   string
  Cmdline  []string
}

func (e *KExecError) Error() string {
  return fmt.Sprintf(
    "Could not execute '%s': Process exited with %i : %s",
    strings.Join(e.Cmdline, " "),
    e.ExitCode,
    e.Stderr,
  )
}

/**
 * A kerberos principal
 */
type KPrincipal struct {
  Primary  string
  Instance string
  Realm    string
}

func ParsePrincipal(principal string) (KPrincipal, error) {
  var ret KPrincipal
  err := ret.SetFromString(principal)
  if err != nil {
    return ret, err
  }
  return ret, nil
}

func (p *KPrincipal) SetFromString(principal string) error {
  // Parse the principal segments
  re := regexp.MustCompile(`^([^\n/@]+)?(?:/([^\n/@]+))?(?:@([^\n/@]+))?$`)
  found := re.FindAllStringSubmatch(principal, -1)

  // Validate parsing
  if len(found) == 0 {
    return fmt.Errorf(
      "Unable to parse the given principal expression: '%s'",
      principal,
    )
  }

  p.Primary = strings.Trim(found[0][1], " \t\n\r")
  p.Instance = strings.Trim(found[0][2], " \t\n\r")
  p.Realm = strings.Trim(found[0][3], " \t\n\r")

  return nil
}

func (p *KPrincipal) Full() string {
  str := p.Primary
  if p.Instance != "" {
    str += "/" + p.Instance
  }
  if p.Realm != "" {
    str += "@" + p.Realm
  }
  return str
}

func (p *KPrincipal) String() string {
  return p.Full()
}
func (p *KPrincipal) MarshalJSON() ([]byte, error) {
  return json.Marshal(p.Full())
}
func (p *KPrincipal) UnmarshalJSON(b []byte) error {
  var expr string
  err := json.Unmarshal(b, &expr)
  if err != nil {
    return err
  }

  err = p.SetFromString(expr)
  if err != nil {
    return err
  }

  return nil
}

/**
 * CreateKAdminClien Create a KAdmin client using the kadmin binary from the given argument
 */
func createKAdminClient(kadmin string) (*KAdminClient, error) {
  path, err := exec.LookPath(kadmin)
  if err != nil {
    return nil, fmt.Errorf("Unable to lookup kadmin")
  }

  client := &KAdminClient{
    KAdminPath: path,
  }

  apiFlavor, err := client.detectCliApiFlavor(path)
  if err != nil {
    return nil, fmt.Errorf("Unable to detect the API flavor of the kadmin utility")
  }

  client.KAdminApiFlavor = apiFlavor
  return client, nil
}

/**
 * Tries to find out if this kexec comes from heimdal or MIT kerberos
 */
func (c *KAdminClient) detectCliApiFlavor(kadmin string) (string, error) {
  _, serr, err := c.exec("-v")
  if err != nil {
    // Check if the CLI does not support the '-v' command. In this case
    // it will show an error and a usage prompt.
    if kerr, ok := err.(*KExecError); ok {
      if strings.Contains(kerr.Stderr, "Usage: ") {
        // MIT kadmin does not have a version command
        return API_MIT, nil
      }
    }

    // Otherwise that's a legit error
    return "", err
  }

  // Heimdal respects the version query and kindly replies with a version string
  if strings.Contains(serr, "Heimdal") {
    return API_HL5, nil
  }

  // If we reached this point we were not able to detect the API flavor to use
  return "", fmt.Errorf("Unknown response from kadmin binary")
}

/**
 * AddPrincipals Adds one or more principals on KDC
 */

func (c *KAdminClient) AddPrincipals(principals []KPrincipal) error {
  switch c.KAdminApiFlavor {
  case API_HL5: // Heimdal API Flavor
    args := []string{
      "-l",
      "add",
      "--use-defaults",
      "--random-password",
    }
    for _, p := range principals {
      args = append(args, p.Full())
    }

    // Call-out to KDC to add all the principals at once
    _, _, err := c.exec(args...)
    return err

  case API_MIT: // MIT API Flavor
    for _, p := range principals {
      args := []string{
        "-r",
        "LOCAL",
        "addprinc",
        "-randkey",
        p.Full(),
      }

      // Call-out to KDC to add a single principal
      _, _, err := c.exec(args...)
      if err != nil {
        return err
      }
    }

    // Completed
    return nil

  default:
    return fmt.Errorf("Unknown KDC CLI API flavor")

  }
}

/**
 * HasPrincipal Checks if the given principal exists
 */
func (c *KAdminClient) HasPrincipal(p KPrincipal) (bool, error) {
  switch c.KAdminApiFlavor {
  case API_HL5: // Heimdal API Flavor
    _, _, err := c.exec("-l", "list", "-l", p.Full())
    if err != nil {
      // Check for missing principal
      if kerr, ok := err.(*KExecError); ok {
        if strings.Contains(kerr.Stderr, "Principal does not exist") {
          return false, nil
        }
      }

      // Anything else is an error
      return false, err
    }

    // If it worked out smoothly, the principal exist
    return true, nil

  case API_MIT: // MIT API Flavor
    _, _, err := c.exec("-r", "LOCAL", "getprinc", p.Full())
    if err != nil {
      // Check for missing principal
      if kerr, ok := err.(*KExecError); ok {
        if strings.Contains(kerr.Stderr, "Principal does not exist") {
          return false, nil
        }
      }

      // Anything else is an error
      return false, err
    }

    // If it worked out smoothly, the principal exist
    return true, nil

  default:
    return false, fmt.Errorf("Unknown KDC CLI API flavor")

  }
}

/**
 * AddMissingPrincipals Behaves similarly to AddPrincipals, but it does not throw error if principals already exists
 */
func (c *KAdminClient) AddMissingPrincipals(principals []KPrincipal) error {
  var missingPrincipals []KPrincipal

  // Find missing principals
  for _, p := range principals {
    ok, err := c.HasPrincipal(p)
    if err != nil {
      return fmt.Errorf("Unable to check if principal '%s' exists: %s", p.Full(), err.Error())
    }

    // Collect missing
    if !ok {
      missingPrincipals = append(missingPrincipals, p)
    }
  }

  // Check for empty cases
  if len(missingPrincipals) == 0 {
    return nil
  }

  return c.AddPrincipals(missingPrincipals)
}

/**
 * GetKeytab Creates a keytab file for the given principals
 */
func (c *KAdminClient) GetKeytabForPrincipals(principals []KPrincipal) ([]byte, error) {
  // Create a temporary file for the keytab
  tmpFile, err := ioutil.TempFile("", "keytab")
  if err != nil {
    return nil, fmt.Errorf("Unable to create a temporary keytab file")
  }

  // First of all, make sure that the file does not exist beforehand
  // This is important, because the MIT flavor of KDC will try to read it, if
  // it already exists, and an empty file is an invalid file!
  tmpFile.Close()
  os.Remove(tmpFile.Name())

  // And don't pollute the filesystem when we are done
  defer os.Remove(tmpFile.Name())

  switch c.KAdminApiFlavor {
  case API_HL5: // Heimdal API Flavor
    // Prepare arguments to generate a keytab file from given princiapls
    args := []string{
      "-l",
      "ext",
      "-k",
      tmpFile.Name(),
    }
    for _, p := range principals {
      args = append(args, p.Full())
    }

    // Call-out to KDC
    _, _, err = c.exec(args...)
    if err != nil {
      return nil, err
    }

  case API_MIT: // MIT API Flavor
    args := []string{
      "-r",
      "LOCAL",
      "ktadd",
      "-k",
      tmpFile.Name(),
    }
    for _, p := range principals {
      args = append(args, p.Full())
    }

    // Call-out to KDC
    _, _, err = c.exec(args...)
    if err != nil {
      return nil, err
    }

  default:
    return nil, fmt.Errorf("Unknown KDC CLI API flavor")

  }

  // Re-open th efile for reading
  tmpFile, err = os.Open(tmpFile.Name())
  if err != nil {
    return nil, fmt.Errorf("Unable to find the keytab file")
  }
  defer tmpFile.Close()

  // Collect keytab contents
  buf := bytes.NewBuffer(nil)
  _, err = io.Copy(buf, tmpFile)
  if err != nil {
    return nil, fmt.Errorf("Unable to read keytab contents: %s", err.Error())
  }

  return buf.Bytes(), nil
}

/**
 * ListPrincipals Lists principals in the kerberos registry, optionally matching the given filter
 */
func (c *KAdminClient) ListPrincipals(filter string) ([]KPrincipal, error) {
  var list string = ""
  var err error
  if filter == "" {
    filter = "*"
  }

  switch c.KAdminApiFlavor {
  case API_HL5: // Heimdal API Flavor
    list, _, err = c.exec("-l", "list", "-l", filter)
    if err != nil {
      return nil, err
    }

    // Parse kadim STDOUT as principals in long format
    return ParsePrincipalsKadminLong(list)

  case API_MIT: // MIT API Flavor
    list, _, err = c.exec("-r", "LOCAL", "listprincs", filter)
    if err != nil {
      return nil, err
    }

    return ParsePrincipalsBytes([]byte(list))

  default:
    return nil, fmt.Errorf("Unknown KDC CLI API flavor")

  }
}

/**
 * exec Forward a command to KAdmin CLI utility
 *
 * @param exec ...string - The arguments to pass to kadmin)
 */
func (c *KAdminClient) exec(args ...string) (string, string, error) {
  var stdout, stderr bytes.Buffer
  fmt.Printf("Executing: %s %s\n", c.KAdminPath, strings.Join(args, " "))

  // Exec command
  cmd := exec.Command(c.KAdminPath, args...)
  cmd.Stdout = &stdout
  cmd.Stderr = &stderr
  err := cmd.Run()

  fmt.Printf(">>> Stdout\n%s<<<\n", string(stdout.Bytes()))
  fmt.Printf(">>> Stderr\n%s<<<\n", string(stderr.Bytes()))

  if err != nil {
    if exiterr, ok := err.(*exec.ExitError); ok {
      if status, ok := exiterr.Sys().(syscall.WaitStatus); ok {
        return "", "", &KExecError{
          ExitCode: status.ExitStatus(),
          Stderr:   string(stderr.Bytes()),
          Cmdline:  args,
        }
      }
    }

    return "", "", &KExecError{
      ExitCode: -1,
      Stderr:   err.Error(),
      Cmdline:  args,
    }
  }

  // Return (stdout, stderr) pair
  return string(stdout.Bytes()), string(stderr.Bytes()), nil
}
