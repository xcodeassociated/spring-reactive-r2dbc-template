#!/bin/bash

grpcurl -d '{ "data":"123" }' -plaintext localhost:9082 com.softeno.template.grpc.SampleGrpcService.Echo

