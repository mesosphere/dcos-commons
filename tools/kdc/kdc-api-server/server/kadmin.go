package main

import (
  "bytes"
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

  // Parse the principal segments
  re := regexp.MustCompile(`^([^\/]+)\/([^@]+)@(.*)$`)
  found := re.FindAllStringSubmatch(principal, -1)

  // Validate parsing
  if len(found) == 0 {
    return ret, fmt.Errorf(
      "Unable to parse the given principal expression: '%s'",
      principal,
    )
  }

  ret.Primary = found[0][1]
  ret.Instance = found[0][2]
  ret.Realm = found[0][3]

  return ret, nil
}

func (p *KPrincipal) Full() string {
  return fmt.Sprintf("%s/%s@%s", p.Primary, p.Instance, p.Realm)
}

/*
CreateKAdminClien Create a KAdmin client using the kadmin binary from the given argument
*/
func CreateKAdminClient(kadmin string) (*KAdminClient, error) {
  path, err := exec.LookPath(kadmin)
  if err != nil {
    return nil, fmt.Errorf("Unable to lookup kadmin")
  }

  return &KAdminClient{
    KAdminPath: path,
  }, nil
}

/*
AddPrincipals Adds one or more principals on KDC
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

  // Call-out to KDC
  _, _, err := c.exec(args...)
  return err
}

/*
GetKeytab Creates a keytab file for the given principals
*/
func (c *KAdminClient) GetKeytab(principals []KPrincipal) ([]byte, error) {
  // Create a temporary file for the keytab
  tmpfile, err := ioutil.TempFile("", "keytab")
  if err != nil {
    return nil, fmt.Errorf("Unable to create a temporary keytab file")
  }
  defer os.Remove(tmpfile.Name()) // clean up

  // Generate keytab
  args := []string{
    "-l",
    "ext",
    "-k",
    tmpfile.Name(),
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
  _, err = io.Copy(buf, tmpfile)
  if err != nil {
    return nil, fmt.Errorf("Unable to read keytab contents: %s", err.Error())
  }

  // Close the tempfile
  tmpfile.Close()

  return buf.Bytes(), nil
}

/*
ListPrincipals Lists principals in the kerberos registry, optionally matching the given filter
*/
func (c *KAdminClient) ListPrincipals(filter string) ([]KPrincipal, error) {
  if filter == "" {
    filter = "*"
  }

  // Enumerate the principals in stdout
  list, _, err := c.exec("-l", "list", filter)
  if err != nil {
    return nil, err
  }

  // Parse them
  return ParsePrincipals(list)
}

/*
exec Forward a command to KAdmin CLI utility

@param exec ...string - The arguments to pass to kadmin)
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

  // Return stdout
  return string(stdout.Bytes()), string(stderr.Bytes()), nil
}
