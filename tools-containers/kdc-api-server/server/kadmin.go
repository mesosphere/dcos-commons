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
 * Kadmin interface
 */
type KAdminClient struct {
  KAdminPath string
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
  return fmt.Sprintf("%s/%s@%s", p.Primary, p.Instance, p.Realm)
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

  return &KAdminClient{
    KAdminPath: path,
  }, nil
}

/**
 * AddPrincipals Adds one or more principals on KDC
 */
func (c *KAdminClient) AddPrincipals(principals []KPrincipal) error {
  args := []string{
    "-l",
    "add",
    "--use-defaults",
    "--random-password",
  }
  for _, p := range principals {
    args = append(args, p.Full())
  }

  // Call-out to KDC to add the principals
  _, _, err := c.exec(args...)
  return err
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
 * HasPrincipal Checks if the given principal exists
 */
func (c *KAdminClient) HasPrincipal(p KPrincipal) (bool, error) {
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
  defer tmpFile.Close()
  defer os.Remove(tmpFile.Name()) // Don't pollute the filesystem

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
  if filter == "" {
    filter = "*"
  }

  // Enumerate the principals in stdout
  list, _, err := c.exec("-l", "list", "-l", filter)
  if err != nil {
    return nil, err
  }

  // Parse kadim STDOUT as principals
  return ParsePrincipalsKadminLong(list)
}

/**
 * exec Forward a command to KAdmin CLI utility
 *
 * @param exec ...string - The arguments to pass to kadmin)
 */
func (c *KAdminClient) exec(args ...string) (string, string, error) {
  var stdout, stderr bytes.Buffer

  // Exec command
  cmd := exec.Command(c.KAdminPath, args...)
  cmd.Stdout = &stdout
  cmd.Stderr = &stderr
  err := cmd.Run()
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
