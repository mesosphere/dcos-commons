package main

import (
	"fmt"
	"os"
	"os/exec"
	"strings"
	"time"

	"github.com/mesosphere/dcos-commons/cli"
	"github.com/mesosphere/dcos-commons/cli/config"
	"gopkg.in/alecthomas/kingpin.v2"
)

func main() {
	app := cli.New()

	cli.HandleDefaultSections(app)

	handleSQLSection(app)
	handleBackupRestoreSection(app)

	kingpin.MustParse(app.Parse(cli.GetArguments()))
}

type backupRestoreHandler struct {
	database           string
	awsAccessKeyID     string
	awsSecretAccessKey string
	awsDefaultRegion   string
	s3BucketName       string
	s3DirPath          string
	backupDir          string
}

func (cmd *backupRestoreHandler) backup(c *kingpin.ParseContext) error {
	fmt.Printf("Backing up database [%s] to bucket [%s]...\n", cmd.database, cmd.s3BucketName)

	var dcosCmd []string
	database := fmt.Sprintf("--params=DATABASE_NAME=%s", cmd.database)
	s3BucketName := fmt.Sprintf("--params=S3_BUCKET_NAME=%s", cmd.s3BucketName)

	var awsAccessKeyID string
	if cmd.awsAccessKeyID != "" {
		awsAccessKeyID = fmt.Sprintf("--params=AWS_ACCESS_KEY_ID=%s", cmd.awsAccessKeyID)
	} else {
		awsAccessKeyID = fmt.Sprintf("--params=AWS_ACCESS_KEY_ID=%s", os.Getenv("AWS_ACCESS_KEY_ID"))
	}

	var awsSecretAccessKey string
	if cmd.awsSecretAccessKey != "" {
		awsSecretAccessKey = fmt.Sprintf("--params=AWS_SECRET_ACCESS_KEY=%s", cmd.awsSecretAccessKey)
	} else {
		awsSecretAccessKey = fmt.Sprintf("--params=AWS_SECRET_ACCESS_KEY=%s", os.Getenv("AWS_SECRET_ACCESS_KEY"))
	}

	var awsDefaultRegion string
	if cmd.awsDefaultRegion != "" {
		awsDefaultRegion = fmt.Sprintf("--params=AWS_DEFAULT_REGION=%s", cmd.awsDefaultRegion)
	} else {
		awsDefaultRegion = fmt.Sprintf("--params=AWS_DEFAULT_REGION=%s", "us-west-1")
	}

	var s3DirPath string
	if cmd.s3DirPath != "" {
		s3DirPath = fmt.Sprintf("--params=S3_DIR_PATH=%s", cmd.s3DirPath)
	} else {
		s3DirPath = fmt.Sprintf("--params=S3_DIR_PATH=%s", "cockroach-backups")
	}

	var backupDir string
	if cmd.backupDir != "" {
		backupDir = fmt.Sprintf("--params=BACKUP_DIR=%s", cmd.backupDir)
	} else {
		t := time.Now()
		backupDir = fmt.Sprintf("--params=BACKUP_DIR=%s", t.Format("2006-01-02_15:04:05"))
	}

	dcosCmd = append(dcosCmd,
		config.ServiceName,
		"plan",
		"start",
		"backup",
		database,
		awsAccessKeyID,
		awsSecretAccessKey,
		awsDefaultRegion,
		s3BucketName,
		s3DirPath,
		backupDir)
	runDcosCommand(dcosCmd...)
	return nil
}

func (cmd *backupRestoreHandler) restore(c *kingpin.ParseContext) error {
	fmt.Printf("Restoring database [%s] with backup [%s] from bucket [%s]...\n", cmd.database, cmd.backupDir, cmd.s3BucketName)

	var dcosCmd []string
	database := fmt.Sprintf("--params=DATABASE_NAME=%s", cmd.database)
	s3BucketName := fmt.Sprintf("--params=S3_BUCKET_NAME=%s", cmd.s3BucketName)
	backupDir := fmt.Sprintf("--params=BACKUP_DIR=%s", cmd.backupDir)

	var awsAccessKeyID string
	if cmd.awsAccessKeyID != "" {
		awsAccessKeyID = fmt.Sprintf("--params=AWS_ACCESS_KEY_ID=%s", cmd.awsAccessKeyID)
	} else {
		awsAccessKeyID = fmt.Sprintf("--params=AWS_ACCESS_KEY_ID=%s", os.Getenv("AWS_ACCESS_KEY_ID"))
	}

	var awsSecretAccessKey string
	if cmd.awsSecretAccessKey != "" {
		awsSecretAccessKey = fmt.Sprintf("--params=AWS_SECRET_ACCESS_KEY=%s", cmd.awsSecretAccessKey)
	} else {
		awsSecretAccessKey = fmt.Sprintf("--params=AWS_SECRET_ACCESS_KEY=%s", os.Getenv("AWS_SECRET_ACCESS_KEY"))
	}

	var awsDefaultRegion string
	if cmd.awsDefaultRegion != "" {
		awsDefaultRegion = fmt.Sprintf("--params=AWS_DEFAULT_REGION=%s", cmd.awsDefaultRegion)
	} else {
		awsDefaultRegion = fmt.Sprintf("--params=AWS_DEFAULT_REGION=%s", "us-west-1")
	}

	var s3DirPath string
	if cmd.s3DirPath != "" {
		s3DirPath = fmt.Sprintf("--params=S3_DIR_PATH=%s", cmd.s3DirPath)
	} else {
		s3DirPath = fmt.Sprintf("--params=S3_DIR_PATH=%s", "cockroach-backups")
	}

	dcosCmd = append(dcosCmd,
		config.ServiceName,
		"plan",
		"start",
		"restore",
		database,
		awsAccessKeyID,
		awsSecretAccessKey,
		awsDefaultRegion,
		s3BucketName,
		s3DirPath,
		backupDir)
	runDcosCommand(dcosCmd...)
	return nil
}

func handleBackupRestoreSection(app *kingpin.Application) {
	cmd := &backupRestoreHandler{}

	backup := app.Command("backup", "Backup specified database to AWS S3 bucket").Action(cmd.backup)
	backup.Arg("database", "Database to back up").Required().StringVar(&cmd.database)
	backup.Flag("aws-access-key", "AWS Access Key").Short('k').StringVar(&cmd.awsAccessKeyID)
	backup.Flag("aws-secret-key", "AWS Secret Key").Short('s').StringVar(&cmd.awsSecretAccessKey)
	backup.Arg("s3-bucket", "AWS S3 bucket name").Required().StringVar(&cmd.s3BucketName)
	backup.Flag("s3-dir", "AWS S3 target path").Short('p').StringVar(&cmd.s3DirPath)
	backup.Flag("s3-backup-dir", "Target path within s3-dir").Short('l').StringVar(&cmd.backupDir)
	backup.Flag("region", "AWS region").Short('r').StringVar(&cmd.awsDefaultRegion)

	restore := app.Command("restore", "Restore specified backup from AWS S3 bucket").Action(cmd.restore)
	restore.Arg("database", "Database to back up").Required().StringVar(&cmd.database)
	restore.Flag("aws-access-key", "AWS Access Key").Short('k').StringVar(&cmd.awsAccessKeyID)
	restore.Flag("aws-secret-key", "AWS Secret Key").Short('s').StringVar(&cmd.awsSecretAccessKey)
	restore.Arg("s3-bucket", "AWS S3 bucket name").Required().StringVar(&cmd.s3BucketName)
	restore.Flag("s3-dir", "AWS S3 target path").Short('p').StringVar(&cmd.s3DirPath)
	restore.Arg("s3-backup-dir", "Target path within s3-dir").Required().StringVar(&cmd.backupDir)
	restore.Flag("region", "AWS region").Short('r').StringVar(&cmd.awsDefaultRegion)
}

type sqlHandler struct {
	database string
	user     string
	execute  string
}

func (cmd *sqlHandler) sql(c *kingpin.ParseContext) error {
	var dcosCmd []string
	cockroachHostFlag := fmt.Sprintf("--host=internal.%s.l4lb.thisdcos.directory", config.ServiceName)
	cockroachTask := fmt.Sprintf("%s-1-node-join", config.ServiceName)

	var dcosFlag string
	if cmd.execute != "" {
		dcosFlag = "-i"
	} else {
		dcosFlag = "-it"
	}

	dcosCmd = append(dcosCmd,
		"task",
		"exec",
		dcosFlag,
		cockroachTask,
		"./cockroach",
		"sql",
		"--insecure",
		cockroachHostFlag)

	if cmd.database != "" {
		dcosCmd = append(dcosCmd, "-d", cmd.database)
	}
	if cmd.user != "" {
		dcosCmd = append(dcosCmd, "-u", cmd.user)
	}
	if cmd.execute != "" {
		dcosCmd = append(dcosCmd, "-e", cmd.execute)
	}

	runDcosCommand(dcosCmd...)
	return nil
}

func runDcosCommand(arg ...string) {
	cmd := exec.Command("dcos", arg...)
	cmd.Stdin = os.Stdin
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	err := cmd.Run()
	if err != nil {
		fmt.Printf("[Error] %s\n\n", err)
		fmt.Printf("Unable to run DC/OS command: %s\n", strings.Join(arg, " "))
		fmt.Printf("Make sure your PATH includes the 'dcos' executable.\n")
	}
}

func handleSQLSection(app *kingpin.Application) {
	cmd := &sqlHandler{}
	sql := app.Command("sql", "Opens interactive Cockroachdb SQL shell").Action(cmd.sql)
	sql.Flag("database", "The database to connect to.").Short('d').StringVar(&cmd.database)
	sql.Flag("user", "The user connecting to the database. The user must have privileges for any statement executed.").Short('u').StringVar(&cmd.user)
	sql.Flag("execute", "SQL command to execute. Will open interactive shell if omitted.").Short('e').StringVar(&cmd.execute)
}
