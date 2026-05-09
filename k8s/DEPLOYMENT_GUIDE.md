# Kubernetes Deployment Guide

## Prerequisites
- Kubernetes cluster (v1.20+)
- kubectl configured
- Docker image pushed to registry

## Deploy to Kubernetes

### 1. Create namespace
```bash
kubectl create namespace sentiment-ledger
```

### 2. Create secrets
```bash
kubectl create secret generic sentiment-ledger-secrets \
  --from-literal=mongodb-uri='<MONGO-DB-URI>' \
  --from-literal=gemini-api-key='<API-KEY>' \
  -n sentiment-ledger
```

### 3. Apply configurations
```bash
kubectl apply -f k8s/configmap.yaml -n sentiment-ledger
kubectl apply -f k8s/deployment.yaml -n sentiment-ledger
kubectl apply -f k8s/service.yaml -n sentiment-ledger
kubectl apply -f k8s/hpa.yaml -n sentiment-ledger
```

### 4. Verify deployment
```bash
kubectl get pods -n sentiment-ledger
kubectl logs -f deployment/sentiment-ledger -n sentiment-ledger
```

### 5. Port forward for local testing
```bash
kubectl port-forward svc/sentiment-ledger-service 8080:80 -n sentiment-ledger
```

## Monitoring

### View metrics
```bash
kubectl exec -it <pod-name> -- curl localhost:9090/metrics
```

### HPA status
```bash
kubectl get hpa -n sentiment-ledger
```