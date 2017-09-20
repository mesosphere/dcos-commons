package minuteman

type NetConf struct {
	Enable bool   `json:"enable", omitempty"`
	Path   string `json:"path, omitempty"`
}
