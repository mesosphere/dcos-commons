package main

import (
	"bytes"
	"flag"
	"fmt"
	"io/ioutil"
	"log"
	"net"
	"os"
	"os/exec"
	"path"
	"path/filepath"
	"sort"
	"strings"
	"time"

	// TODO switch to upstream once https://github.com/hoisie/mustache/pull/57 is merged:
	"github.com/aryann/difflib"
	"github.com/nickbp/mustache"
)

// arg handling

const (
	configTemplatePrefix = "CONFIG_TEMPLATE_"
	resolveRetryDelay    = time.Duration(1) * time.Second
	mesos_dns            = "mesos"
)

var verbose = false

type args struct {
	// Whether to print the container environment up-front
	printEnvEnabled bool

	// Whether to enable host resolution at startup
	resolveEnabled bool
	// Host resolution. Empty slice means disabled.
	resolveHosts []string
	// Timeout across all hosts. Zero means timeout disabled.
	resolveTimeout time.Duration

	// Whether to enable template logic
	templateEnabled bool
	// Max supported bytes or 0 for no limit
	templateMaxBytes int64

	// Install certs from .ssl into JRE/lib/security/cacerts
	installCerts bool

	// Get Task IP
	getTaskIp bool

	// Whether to decode java keystore secrets files from base64 to binary
	decodeKeystores bool
}

func parseArgs() args {
	args := args{}
	flag.BoolVar(&verbose, "verbose", verbose, "Extra logging of requests/responses.")

	flag.BoolVar(&args.printEnvEnabled, "print-env", true,
		"Whether to print the process environment.")

	flag.BoolVar(&args.resolveEnabled, "resolve", true,
		"Whether to enable the step of waiting for hosts to resolve. "+
			"May be disabled for faster startup when not needed.")
	var rawHosts string
	defaultHostString := "<TASK_NAME>.<FRAMEWORK_HOST>"
	flag.StringVar(&rawHosts, "resolve-hosts", defaultHostString,
		"Comma-separated list of hosts to resolve. Defaults to the hostname of the task itself.")
	flag.DurationVar(&args.resolveTimeout, "resolve-timeout", time.Duration(5)*time.Minute,
		"Duration to wait for all host resolutions to complete, or zero to wait indefinitely.")

	flag.BoolVar(&args.templateEnabled, "template", true,
		fmt.Sprintf("Whether to enable processing of configuration templates advertised by %s* "+
			"env vars.", configTemplatePrefix))
	flag.Int64Var(&args.templateMaxBytes, "template-max-bytes", 1024*1024,
		"Largest template file that may be processed, or zero for no limit.")
	flag.BoolVar(&args.installCerts, "install-certs", true,
		"Whether to install certs from .ssl to the JRE.")

	flag.BoolVar(&args.getTaskIp, "get-task-ip", false, "Print task IP")

	flag.BoolVar(&args.decodeKeystores, "decode-keystores", true,
		"Decodes any *.keystore.base64 and *.truststore.base64 files in mesos "+
			"sandbox directory from BASE64 encoding to binary format and stores "+
			"them without base64 suffix")

	flag.Parse()

	// Note: Parse this argument AFTER flag.Parse(), in case user is just running '--help'
	if args.resolveEnabled && rawHosts == defaultHostString && !args.getTaskIp {
		// Note: only build the default resolve value (requiring envvars) *after* we know
		// the user didn't provide hosts of their own.
		taskName, taskNameOk := os.LookupEnv("TASK_NAME")
		frameworkHost, frameworkHostOk := os.LookupEnv("FRAMEWORK_HOST")
		if !taskNameOk || !frameworkHostOk {
			printEnv()
			log.Fatalf("Missing required envvar(s) to build default -resolve-hosts value. " +
				"Either specify -resolve-hosts or provide these envvars: TASK_NAME, FRAMEWORK_HOST.")
		}
		args.resolveHosts = []string{fmt.Sprintf("%s.%s", taskName, frameworkHost)}
	} else {
		args.resolveHosts = splitAndClean(rawHosts, ",")
	}

	return args
}

func splitAndClean(s string, sep string) []string {
	rawSplit := strings.Split(s, sep)
	split := make([]string, 0, len(rawSplit))
	for _, raw := range rawSplit {
		trimmed := strings.TrimSpace(raw)
		if len(trimmed) != 0 {
			split = append(split, trimmed)
		}
	}
	return split
}

// env print

func printEnv() {
	env := os.Environ()
	sort.Strings(env)
	log.Printf("Bootstrapping with environment:\n%s", strings.Join(env, "\n"))
}

// dns resolve

func waitForResolve(resolveHosts []string, resolveTimeout time.Duration) {
	var timer *time.Timer
	if resolveTimeout == 0 {
		timer = nil
	} else {
		timer = time.NewTimer(resolveTimeout)
	}
	for _, host := range resolveHosts {
		log.Printf("Waiting for '%s' to resolve...", host)
		for {
			result, err := net.LookupHost(host)

			// Check result, exit loop if suceeded:
			if err != nil {
				if verbose {
					log.Printf("Lookup failed: %s", err)
				}
			} else if len(result) == 0 {
				if verbose {
					log.Printf("No results for host '%s'", host)
				}
			} else {
				log.Printf("Resolved '%s' => %s", host, result)
				break
			}

			// Check timeout:
			if timer != nil {
				select {
				case _, ok := <-timer.C:
					if ok {
						log.Fatalf("Time ran out while resolving '%s'. "+
							"Customize timeout with -resolve-timeout, or use -verbose to see attempts.", host)
					} else {
						log.Fatalf("Internal error: Channel closed")
					}
				default:
					// do nothing
				}
			}

			// Wait before retry:
			time.Sleep(resolveRetryDelay)
		}
	}

	if verbose {
		log.Printf("Hosts resolved, continuing bootstrap.")
	}

	// Clean up:
	if !timer.Stop() {
		<-timer.C
	}
}

// template download/read and render

func openTemplate(inPath string, source string, templateMaxBytes int64) []byte {
	sandboxDir, found := os.LookupEnv("MESOS_SANDBOX")
	if !found {
		log.Fatalf("Missing required envvar: MESOS_SANDBOX")
	}
	templatePath := path.Join(sandboxDir, inPath)
	info, err := os.Stat(templatePath)
	if err != nil && os.IsNotExist(err) {
		log.Fatalf("Path from %s doesn't exist: %s", source, templatePath)
	}
	if !info.Mode().IsRegular() {
		cwd, err := os.Getwd()
		if err != nil {
			cwd = err.Error()
		}
		log.Fatalf("Path from %s is not a regular file: %s (cwd=%s)",
			source, templatePath, cwd)
	}
	if templateMaxBytes != 0 && info.Size() > templateMaxBytes {
		log.Fatalf("File '%s' from %s is %d bytes, exceeds maximum %d bytes",
			templatePath, source, info.Size(), templateMaxBytes)
	}

	data, err := ioutil.ReadFile(templatePath)
	if err != nil {
		log.Fatalf("Failed to read file from %s at '%s': %s", source, templatePath, err)
	}
	return data
}

func renderTemplate(origContent string, outPath string, envMap map[string]string, source string) {
	dirpath, _ := path.Split(outPath)
	template, err := mustache.ParseStringInDir(origContent, dirpath)
	if err != nil {
		log.Fatalf("Failed to parse template content from %s at '%s': %s", source, outPath, err)
	}
	newContent := template.Render(envMap)

	// Print a nice debuggable diff of the changes before they're written.
	log.Printf("Writing rendered '%s' from %s with the following changes (%d bytes -> %d bytes):",
		outPath, source, len(origContent), len(newContent))
	for _, diffRec := range difflib.Diff(strings.Split(origContent, "\n"), strings.Split(newContent, "\n")) {
		fmt.Fprintf(os.Stderr, "%s\n", diffRec)
	}

	err = ioutil.WriteFile(outPath, []byte(newContent), 0666) // mode shouldn't matter: file should exist
	if err != nil {
		log.Fatalf("Failed to write rendered template from %s to '%s': %s", source, outPath, err)
	}
}

func renderTemplates(templateMaxBytes int64) {
	// Populate map with all envvars:
	envMap := make(map[string]string)
	for _, entry := range os.Environ() {
		keyVal := strings.SplitN(entry, "=", 2) // entry: "key=val"
		envMap[keyVal[0]] = keyVal[1]
	}

	// Handle CONFIG_TEMPLATE_* entries in env, passing them the full env map that we'd built above:
	for _, entry := range os.Environ() {
		if !strings.HasPrefix(entry, configTemplatePrefix) {
			continue
		}

		envKeyVal := strings.SplitN(entry, "=", 2)      // entry: "CONFIG_TEMPLATE_<name>=<src-path>,<dest-path>"
		srcDest := strings.SplitN(envKeyVal[1], ",", 2) // value: "<src-path>,<dest-path>"
		if len(srcDest) != 2 {
			log.Fatalf("Provided value for %s is invalid: Should be two strings separated by a comma, got: %s",
				envKeyVal[0], envKeyVal[1])
		}

		source := fmt.Sprintf("envvar '%s'", envKeyVal[0])
		data := openTemplate(srcDest[0], source, templateMaxBytes)
		renderTemplate(string(data), srcDest[1], envMap, source)
	}
}

func installDCOSCertIntoJRE() {
	mesosSandbox := os.Getenv("MESOS_SANDBOX")
	sslDir := filepath.Join(mesosSandbox, ".ssl")

	// Check if .ssl directory is present
	sslDirExists, err := isDir(sslDir)
	if !sslDirExists || err != nil {
		log.Printf("No $MESOS_SANDBOX/.ssl directory found. Cannot install certificate. Error: %s", err)
		return
	}

	// Check if .ssl/ca.crt certificate is present (< 1.10)
	// or if .ssl/ca-bundle.crt certificate is present (1.10+)
	certPath := filepath.Join(mesosSandbox, ".ssl", "ca-bundle.crt")
	certExists, err := isFile(certPath)
	if !certExists || err != nil {
		log.Printf("No $MESOS_SANDBOX/.ssl/ca-bundle.crt file found. Error: %s. Checking pre 1.10 location", err)
		certPath = filepath.Join(mesosSandbox, ".ssl", "ca.crt")
		certExists, err = isFile(certPath)
		if !certExists || err != nil {
			log.Printf("No $MESOS_SANDBOX/.ssl/ca.crt file found. Error: %s.", err)
			log.Printf("No CA Cert found in the sandbox. Cannot install certificate. " +
				"This is expected if the cluster is not in STRICT mode.")
			return
		}
	}

	javaHome := os.Getenv("JAVA_HOME")
	if len(javaHome) == 0 {
		log.Printf("No JAVA_HOME provided. Cannot install certs.")
		return
	}

	cacertsPath := filepath.Join(javaHome, "lib", "security", "cacerts")
	keytoolPath := filepath.Join(javaHome, "bin", "keytool")
	cmd := exec.Command(keytoolPath, "-importcert", "-noprompt", "-alias", "dcoscert", "-keystore", cacertsPath,
		"-file", certPath, "-storepass", "changeit")
	var out bytes.Buffer
	cmd.Stdout = &out
	err = cmd.Run()
	if err != nil {
		log.Printf("Failed to install the certificate. Error: %s", err)
		return
	}
	log.Println("Successfully installed the certificate.")
}

func decodeKeystores() error {
	return NewSecretBase64Decoder(os.Getenv("MESOS_SANDBOX")).
		decodeKeystores()
}

func isDir(path string) (bool, error) {
	fi, err := os.Stat(path)
	if err != nil {
		return false, err
	}
	if os.IsNotExist(err) {
		return false, nil
	}
	mode := fi.Mode()
	if mode.IsDir() {
		return true, nil
	}
	return false, nil
}

func isFile(path string) (bool, error) {
	fi, err := os.Stat(path)
	if err != nil {
		return false, err
	}
	if os.IsNotExist(err) {
		return false, nil
	}
	mode := fi.Mode()
	if mode.IsRegular() {
		return true, nil
	}
	return false, nil
}

func GetLocalIPold() string {
	addrs, err := net.InterfaceAddrs()
	if err != nil {
		return ""
	}
	for _, address := range addrs {
		// check the address type and if it is not a loopback the display it
		if ipnet, ok := address.(*net.IPNet); ok && !ipnet.IP.IsLoopback() {
			if ipnet.IP.To4() != nil {
				return ipnet.IP.String()
			}
		}
	}
	return ""
}

func GetLocalIP() (addr string, err error) {
	ip, err := ContainerIP()

	if err != nil {
		return ip.String(), err
	}

	addr = ip.String()
	return ip.String(), err
}

// main

func main() {
	args := parseArgs()

	pod_ip, err := GetLocalIP()
	if err != nil {
		log.Fatalf("Cannot find the container's IP address: ", err)
	}

	err = os.Setenv("LIBPROCESS_IP", pod_ip)
	err = os.Setenv("MESOS_CONTAINER_IP", pod_ip)
	if err != nil {
		log.Fatalf("Failed to SET new LIBPROCESS_IP: ", err)
	}

	if args.getTaskIp {
		log.Printf("Printing new task IP: %s", pod_ip)
		fmt.Printf("%s", pod_ip)
		os.Exit(0)
	}

	if args.printEnvEnabled {
		printEnv()
	}

	if args.resolveEnabled {
		waitForResolve(args.resolveHosts, args.resolveTimeout)
	} else {
		log.Printf("Resolve disabled via -resolve=false: Skipping host resolution")
	}

	if args.templateEnabled {
		renderTemplates(args.templateMaxBytes)
	} else {
		log.Printf("Template handling disabled via -template=false: Skipping any config templates")
	}

	if args.installCerts {
		installDCOSCertIntoJRE()
	}

	if args.decodeKeystores {
		if err := decodeKeystores(); err != nil {
			log.Printf("Error when decoding base64 secrets: %s\n", err)
			os.Exit(0)
		}
	}

	log.Printf("Local IP --> %s", pod_ip)
	log.Printf("SDK Bootstrap successful.")
}
