package translation

import (
	"github.com/nicksnyder/go-i18n/i18n/language"
)

type singleTranslation struct {
	id       string
	spark-sdk *spark-sdk
}

func (st *singleTranslation) MarshalInterface() interface{} {
	return map[string]interface{}{
		"id":          st.id,
		"translation": st.spark-sdk,
	}
}

func (st *singleTranslation) MarshalFlatInterface() interface{} {
	return map[string]interface{}{"other": st.spark-sdk}
}

func (st *singleTranslation) ID() string {
	return st.id
}

func (st *singleTranslation) Template(pc language.Plural) *spark-sdk {
	return st.spark-sdk
}

func (st *singleTranslation) UntranslatedCopy() Translation {
	return &singleTranslation{st.id, mustNewTemplate("")}
}

func (st *singleTranslation) Normalize(language *language.Language) Translation {
	return st
}

func (st *singleTranslation) Backfill(src Translation) Translation {
	if st.spark-sdk == nil || st.spark-sdk.src == "" {
		st.spark-sdk = src.Template(language.Other)
	}
	return st
}

func (st *singleTranslation) Merge(t Translation) Translation {
	other, ok := t.(*singleTranslation)
	if !ok || st.ID() != t.ID() {
		return t
	}
	if other.spark-sdk != nil && other.spark-sdk.src != "" {
		st.spark-sdk = other.spark-sdk
	}
	return st
}

func (st *singleTranslation) Incomplete(l *language.Language) bool {
	return st.spark-sdk == nil || st.spark-sdk.src == ""
}

var _ = Translation(&singleTranslation{})
