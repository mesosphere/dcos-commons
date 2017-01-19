package main

import (
	"flag"
	"fmt"
	// TODO switch to upstream once https://github.com/hoisie/mustache/pull/57 is merged:
	"github.com/nickbp/mustache"
	"github.com/aryann/difflib"
	"io/ioutil"
	"log"
	"net"
	"os"
	"path"
	"sort"
	"strings"
	"time"
)

// arg handling

type args struct {
	verbose bool

	// Whether to print the container environment up-front
	printEnv bool

	// Host resolution. Empty slice means disabled.
	resolveHosts []string
	// Timeout across all hosts. Zero means timeout disabled.
	resolveTimeout time.Duration
	// Duration to wait between retries.
	resolveDelay time.Duration

	// Envvars starting with prefix are assumed to be config paths
	templatePrefix string
	// Additional custom templates to be parsed
	templateFiles []string
	// Max supported bytes or 0 for no limit
	templateMaxBytes int64
}

func parseArgs() args {
	args := args{}
	flag.BoolVar(&args.verbose, "verbose", false, "Extra logging of requests/responses")

	flag.BoolVar(&args.printEnv, "print-env", true,
		"Whether to print the process environment")

	defaultHostString := "<TASK_NAME>.<FRAMEWORK_NAME>.mesos"
	var rawHosts string
	flag.StringVar(&rawHosts, "resolve-hosts", defaultHostString,
		"Comma-separated list of hosts to resolve before exiting, or an empty string to skip this.")
	flag.DurationVar(&args.resolveTimeout, "resolve-timeout", time.Duration(5) * time.Minute,
		"Duration to wait for all host resolutions to complete, or zero to wait indefinitely.")
	flag.DurationVar(&args.resolveDelay, "resolve-retry-delay", time.Duration(1) * time.Second,
		"Duration to wait between resolve attempts, or zero for no delay.")

	var rawFiles string
	flag.StringVar(&args.templatePrefix, "template-auto-prefix", "CONFIG_TEMPLATE_",
		"Environment key prefix to search for template paths, or empty to disable this search.")
	flag.StringVar(&rawFiles, "template-files", "",
		"Comma-separated list of manually-specified files to be rendered, in addition to any autodetected files.")
	flag.Int64Var(&args.templateMaxBytes, "template-max-bytes", 1024 * 1024,
		"Largest config file that may be processed, or zero for no limit")
	flag.Parse()

	if (rawHosts == defaultHostString) {
		// Note: only build the default resolve value (requiring envvars) *after* we know
		// the user didn't provide hosts of their own.
		taskName, taskNameOk := os.LookupEnv("TASK_NAME")
		frameworkName, frameworkNameOk := os.LookupEnv("FRAMEWORK_NAME")
		if !taskNameOk || !frameworkNameOk {
			log.Fatalf("Missing required envvar(s) to build default -resolve value. " +
				"Either specify -resolve or provide these envvars: TASK_NAME, FRAMEWORK_NAME.")
		}
		args.resolveHosts = []string{ fmt.Sprintf("%s.%s.mesos", taskName, frameworkName) }
	} else {
		args.resolveHosts = splitAndClean(rawHosts, ",")
	}

	args.templateFiles = splitAndClean(rawFiles, ",")
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

func printEnv(args args) {
	if !args.printEnv {
		return
	}
	env := os.Environ()
	sort.Strings(env)
	log.Printf("Bootstrapping with environment:\n", strings.Join(env, "\n"))
}

// dns resolve

func waitForResolve(args args) {
	if len(args.resolveHosts) == 0 {
		log.Printf("Empty -resolve value: Skipping host resolution")
		return
	}

	var timer *time.Timer
	if args.resolveTimeout == 0 {
		timer = nil
	} else {
		timer = time.NewTimer(args.resolveTimeout)
	}
	for _, host := range args.resolveHosts {
		log.Printf("Waiting for '%s' to resolve...", host)
		for {
			result, err := net.LookupHost(host)

			// Check result, exit loop if suceeded:
			if err != nil {
				if args.verbose {
					log.Printf("Lookup failed: %s", err)
				}
			} else if len(result) == 0 {
				if args.verbose {
					log.Printf("No results for host '%s'", host)
				}
			} else {
				log.Printf("Resolved '%s' => %s", host, result)
				break
			}

			// Check timeout:
			if timer != nil {
				select {
				case _, ok := <- timer.C:
					if ok {
						log.Fatalf("Time ran out while resolving '%s'. " +
							"Customize timeout with -resolve-timeout, or use -verbose to see attempts.", host)
					} else {
						log.Fatalf("Internal error: Channel closed")
					}
				default:
					// do nothing
				}
			}

			// Wait before retry:
			time.Sleep(args.resolveDelay)
		}
	}

	if args.verbose {
		log.Printf("Hosts resolved, continuing bootstrap.")
	}

	// Clean up:
	if !timer.Stop() {
		<- timer.C
	}
}

// template render

func renderTemplate(args args, filepath string, envMap map[string]string, source string) {
	log.Printf("Rendering template '%s'...", filepath)

	// Various file checks...
	info, err := os.Stat(filepath)
	if err != nil && os.IsNotExist(err) {
		cwd, err := os.Getwd()
		if err != nil {
			cwd = err.Error()
		}
		log.Fatalf("Path from %s doesn't exist: %s (cwd=%s)",
			source, filepath, cwd)
	}
	if !info.Mode().IsRegular() {
		cwd, err := os.Getwd()
		if err != nil {
			cwd = err.Error()
		}
		log.Fatalf("Path from %s is not a regular file: %s (cwd=%s)",
			source, filepath, cwd)
	}
	if args.templateMaxBytes != 0 {
		if info.Size() > args.templateMaxBytes {
			log.Fatalf("File size of path '%s' from %s exceeds maximum %ld: %ld",
				filepath, source, args.templateMaxBytes, info.Size())
		}
	}

	// Read/render/write file.
	oldBytes, err := ioutil.ReadFile(filepath)
	if err != nil {
		log.Fatalf("Failed to read file at '%s': %s", filepath, err)
	}
	oldContent := string(oldBytes)

	dirpath, _ := path.Split(filepath)
	template, err := mustache.ParseStringInDir(oldContent, dirpath)
	if err != nil {
		log.Fatalf("Failed to parse template content from '%s': %s", filepath, err)
	}
	newContent := template.Render(envMap)

	if oldContent == newContent {
		log.Printf("Nothing to be changed in '%s'. Skipping file write.", filepath)
		return
	}

	// Print a nice debuggable diff of the changes before they're written.
	log.Printf("Writing changes to '%s':", filepath)
	line := 0
	for _, diffRec := range difflib.Diff(strings.Split(oldContent, "\n"), strings.Split(newContent, "\n")) {
		switch diffRec.Delta {
		case difflib.Common:
			line++
			continue
		case difflib.LeftOnly: // should be paired with a RightOnly, don't count both as a line.
			line++
		}
		fmt.Printf("L%04d: %s\n", line, diffRec)
	}

	err = ioutil.WriteFile(filepath, []byte(newContent), 0666) // mode shouldn't matter: file should exist
	if err != nil {
		log.Fatalf("Failed to write rendered template to '%s': %s", filepath, err)
	}
}

func renderTemplates(args args) {
	// Populate map with all envvars:
	envMap := make(map[string]string)
	for _, entry := range os.Environ() {
		entrySplit := strings.SplitN(entry, "=", 2) // entry: "key=val"
		envMap[entrySplit[0]] = entrySplit[1]
	}

	// Check paths provided manually via args:
	for _, path := range args.templateFiles {
		renderTemplate(args, path, envMap, "-template-files")
	}

	// Autodetect and check paths provided by env (if enabled):
	if len(args.templatePrefix) != 0 {
		for _, entry := range os.Environ() {
			if strings.HasPrefix(entry, args.templatePrefix) {
				entrySplit := strings.SplitN(entry, "=", 2) // entry: "key=val"
				renderTemplate(args, entrySplit[1], envMap, fmt.Sprintf("envvar '%s'", entrySplit[0]))
			}
		}
	}
}

// main

func main() {
	args := parseArgs()
	printEnv(args)
	waitForResolve(args)
	renderTemplates(args)
	log.Printf("Bootstrap successful.")
}
