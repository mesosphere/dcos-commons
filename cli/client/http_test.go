package client

import (
	"testing"

	"github.com/mesosphere/dcos-commons/cli/config"
)

func TestGetDCOSURL(t *testing.T) {
	intendedUrl := "myurl/"
	config.DcosUrl = "myurl/#/"
	getDCOSURL()
	if config.DcosUrl != intendedUrl {
		t.Fatal("expected %s got %s", intendedUrl, config.DcosUrl)
	}

	intendedUrl = "myurl/#/"
	config.DcosUrl = "myurl/#/#/"
	getDCOSURL()
	if config.DcosUrl != intendedUrl {
		t.Fatal("expected %s got %s", intendedUrl, config.DcosUrl)
	}
}
