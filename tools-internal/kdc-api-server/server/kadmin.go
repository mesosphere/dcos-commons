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
const FLAVOR_HL5 = "Heimdal" // Heimdal (HL5) syntax for the CLI commands
const FLAVOR_MIT = "MIT"     // MIT Kerberos (KRB5) syntax for the CLI commands

/**
 * Kadmin interface
 */
type KAdminClient struct {
  KTUtilPath      string
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
func createKAdminClient(kadmin string, ktutil string) (*KAdminClient, error) {
  kadmin, err := exec.LookPath(kadmin)
  if err != nil {
    return nil, fmt.Errorf("Unable to lookup kadmin")
  }

  if ktutil != "" {
    ktutil, err = exec.LookPath(ktutil)
    if err != nil {
      return nil, fmt.Errorf("Unable to lookup ktutil")
    }
  }

  client := &KAdminClient{
    KAdminPath: kadmin,
    KTUtilPath: ktutil,
  }

  apiFlavor, err := client.detectCliApiFlavor()
  if err != nil {
    return nil, fmt.Errorf("Unable to detect the API flavor of the kadmin utility")
  }

  client.KAdminApiFlavor = apiFlavor
  return client, nil
}

/**
 * Tries to find out if this kexec comes from heimdal or MIT kerberos
 */
func (c *KAdminClient) detectCliApiFlavor() (string, error) {
  _, serr, err := c.kadminExec("-v")
  if err != nil {
    // Check if the CLI does not support the '-v' command. In this case
    // it will show an error and a usage prompt.
    if kerr, ok := err.(*KExecError); ok {
      if strings.Contains(kerr.Stderr, "Usage: ") {
        // MIT kadmin does not have a version command
        return FLAVOR_MIT, nil
      }
    }

    // Otherwise that's a legit error
    return "", err
  }

  // Heimdal respects the version query and kindly replies with a version string
  if strings.Contains(serr, "Heimdal") {
    return FLAVOR_HL5, nil
  }

  // If we reached this point we were not able to detect the API flavor to use
  return "", fmt.Errorf("Unknown response from kadmin binary")
}

/**
 * AddPrincipals Adds one or more principals on KDC
 */
func (c *KAdminClient) AddPrincipals(principals []KPrincipal) error {
  switch c.KAdminApiFlavor {
  case FLAVOR_HL5: // Heimdal API Flavor
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
    _, _, err := c.kadminExec(args...)
    return err

  case FLAVOR_MIT: // MIT API Flavor
    for _, p := range principals {
      args := []string{
        "-r",
        "LOCAL",
        "addprinc",
        "-randkey",
        p.Full(),
      }

      // Call-out to KDC to add a single principal
      _, _, err := c.kadminExec(args...)
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
 * DeletePrincipals removes one or more principals
 */
func (c *KAdminClient) DeletePrincipals(principals []KPrincipal) error {
  switch c.KAdminApiFlavor {
  case FLAVOR_HL5: // Heimdal API Flavor
    args := []string{
      "-l",
      "delete",
    }
    for _, p := range principals {
      args = append(args, p.Full())
    }

    // Call-out to KDC to add all the principals at once
    _, _, err := c.kadminExec(args...)
    return err

  case FLAVOR_MIT: // MIT API Flavor
    for _, p := range principals {
      args := []string{
        "-r",
        "LOCAL",
        "delprinc",
        p.Full(),
      }

      // Call-out to KDC to add a single principal
      _, _, err := c.kadminExec(args...)
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
  case FLAVOR_HL5: // Heimdal API Flavor
    _, _, err := c.kadminExec("-l", "list", "-l", p.Full())
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

  case FLAVOR_MIT: // MIT API Flavor
    _, _, err := c.kadminExec("-r", "LOCAL", "getprinc", p.Full())
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
  case FLAVOR_HL5: // Heimdal API Flavor
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
    _, _, err = c.kadminExec(args...)
    if err != nil {
      return nil, err
    }

  case FLAVOR_MIT: // MIT API Flavor
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
    _, _, err = c.kadminExec(args...)
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
  case FLAVOR_HL5: // Heimdal API Flavor
    list, _, err = c.kadminExec("-l", "list", "-l", filter)
    if err != nil {
      return nil, err
    }

    // Parse kadim STDOUT as principals in long format
    return ParsePrincipalsKadminLong(list)

  case FLAVOR_MIT: // MIT API Flavor
    list, _, err = c.kadminExec("-r", "LOCAL", "listprincs", filter)
    if err != nil {
      return nil, err
    }

    return ParsePrincipalsBytes([]byte(list))

  default:
    return nil, fmt.Errorf("Unknown KDC CLI API flavor")

  }
}

/**
 * Checks if the given principal exists in the keytab file
 */
func (c *KAdminClient) HasPrincipalInKeytab(keytabContents []byte, principal *KPrincipal) (bool, error) {
  // Create a temporary file with the keytab
  tmpFile, err := ioutil.TempFile("", "keytab")
  if err != nil {
    return false, fmt.Errorf("Unable to create a temporary keytab file")
  }
  defer os.Remove(tmpFile.Name())

  // Write the contents to the temp file
  _, err = tmpFile.Write(keytabContents)
  if err != nil {
    tmpFile.Close()
    return false, fmt.Errorf("Unable to write the temporary keytab file contents")
  }
  tmpFile.Close()

  switch c.KAdminApiFlavor {
  case FLAVOR_HL5: // Heimdal API Flavor
    // For Heimdal KDC se *MUST* use the keytab util. The simplest thing we
    // can try is to enumerate all the keys and look for the principal in the list
    sout, _, err := c.ktutilExec("-k", tmpFile.Name(), "list")
    if err != nil {
      return false, err
    }

    return strings.Contains(sout, principal.Full()), nil

  case FLAVOR_MIT: // MIT API Flavor
    // Since we only know of the `kadmin` utility, we can use the `ktrem` command
    // to try removing a principal from a keytab file. If it works, it's there.
    _, serr, err := c.kadminExec("-r", "LOCAL", "ktrem", "-k", tmpFile.Name(), principal.Full())
    if err != nil {
      // Handle explicitly the error when the principal does not exist
      // The error looks like:
      //
      // "kadmin.local: No entry for principal ... exists in keytab WRFILE:keytab"
      //
      if kerr, ok := err.(*KExecError); ok {
        if strings.Contains(kerr.Stderr, "No entry") {
          return false, nil
        }
      }
      return false, err
    }

    // Surprise! Some kadmin flavors don't return an error if an entry does
    // not exist. They just echo it!
    return !strings.Contains(serr, "No entry"), nil
  }

  return false, fmt.Errorf("Unknown KDC CLI API flavor")
}

/**
 * exec Forward a command to KAdmin CLI utility
 *
 * @param exec ...string - The arguments to pass to kadmin)
 */
func (c *KAdminClient) kadminExec(args ...string) (string, string, error) {
  var stdout, stderr bytes.Buffer

  // Exec command
  fmt.Printf("Executing: %s %s\n", c.KAdminPath, strings.Join(args, " "))
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

/**
 * exec Forward a command to KTUtil CLI utility
 *
 * @param exec ...string - The arguments to pass to kadmin)
 */
func (c *KAdminClient) ktutilExec(args ...string) (string, string, error) {
  var stdout, stderr bytes.Buffer
  if c.KTUtilPath == "" {
    return "", "", fmt.Errorf("Path to KTUtil was not provided!")
  }

  // Exec command
  fmt.Printf("Executing: %s %s\n", c.KTUtilPath, strings.Join(args, " "))
  cmd := exec.Command(c.KTUtilPath, args...)
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
