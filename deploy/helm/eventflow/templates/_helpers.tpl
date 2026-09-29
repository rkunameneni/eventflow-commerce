{{/* Common labels for a service. Call with (dict "root" $ "name" $name). */}}
{{- define "eventflow.labels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/part-of: eventflow
app.kubernetes.io/version: {{ .root.Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .root.Chart.Name .root.Chart.Version }}
{{- end }}

{{- define "eventflow.selectorLabels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
{{- end }}

{{/* Per-service settings merged over chart-wide defaults. */}}
{{- define "eventflow.autoscaling" -}}
{{- $svc := .svc | default dict -}}
{{- toYaml (merge (deepCopy ($svc.autoscaling | default dict)) .root.Values.defaults.autoscaling) -}}
{{- end }}

{{- define "eventflow.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{ .Values.serviceAccount.name }}
{{- else -}}
{{ .Values.serviceAccount.name | default "default" }}
{{- end -}}
{{- end }}
