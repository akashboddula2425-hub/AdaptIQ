---
license: apache-2.0
language:
- en
pipeline_tag: text-generation
tags:
- chat
base_model:
- Qwen/Qwen2.5-0.5B-Instruct
---
# Qwen2.5-0.5B-Instruct-MNN

## Introduction
This model is a 4-bit quantized version of the MNN model exported from [Qwen2.5-0.5B-Instruct](https://modelscope.cn/models/qwen/Qwen2.5-0.5B-Instruct/summary) using [llmexport](https://github.com/alibaba/MNN/tree/master/transformers/llm/export).

## Download
```bash
# install huggingface
pip install huggingface
```
```bash
# shell download
huggingface download --model 'taobao-mnn/Qwen2.5-0.5B-Instruct-MNN' --local_dir 'path/to/dir'
```
```python
# SDK download
from huggingface_hub import snapshot_download
model_dir = snapshot_download('taobao-mnn/Qwen2.5-0.5B-Instruct-MNN')
```

```bash
# git clone
git clone https://www.modelscope.cn/taobao-mnn/Qwen2.5-0.5B-Instruct-MNN
```

## Usage
```bash
# clone MNN source
git clone https://github.com/alibaba/MNN.git

# compile
cd MNN
mkdir build && cd build
cmake .. -DMNN_LOW_MEMORY=true -DMNN_CPU_WEIGHT_DEQUANT_GEMM=true -DMNN_BUILD_LLM=true -DMNN_SUPPORT_TRANSFORMER_FUSE=true
make -j

# run
./llm_demo /path/to/Qwen2.5-0.5B-Instruct-MNN/config.json prompt.txt
```

## Document
[MNN-LLM](https://mnn-docs.readthedocs.io/en/latest/transformers/llm.html#)