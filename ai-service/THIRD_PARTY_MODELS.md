# Third-Party Model and Code Notice

## UniversalFakeDetect

- Detector: UniversalFakeDetect (UFD), CLIP ViT-L/14 image features plus a learned linear classifier.
- Upstream project: [WisconsinAIVision/UniversalFakeDetect](https://github.com/WisconsinAIVision/UniversalFakeDetect).
- Paper: [Towards Universal Fake Image Detectors That Generalize Across Generative Models](https://openaccess.thecvf.com/content/CVPR2023/html/Ojha_Towards_Universal_Fake_Image_Detectors_That_Generalize_Across_Generative_Models_CVPR_2023_paper.html), CVPR 2023.
- Upstream repository license: MIT. Deployers must independently confirm that the downloaded checkpoint is covered by terms suitable for their use.
- Checkpoint provenance: the linear-classifier checkpoint must be manually obtained from the release/provisioning instructions in the official upstream project. AuthVault does not redistribute it.

## OpenAI CLIP

- Dependency: [openai/CLIP](https://github.com/openai/CLIP), pinned by AuthVault to Git commit `d05afc436d78f1c48dc0dbf8e5980a9d471f35f6`.
- Upstream repository license: MIT.
- Checkpoint provenance: the ViT-L/14 checkpoint must be manually obtained through the official OpenAI CLIP project. AuthVault does not redistribute it or download it during application operation.

Third-party checkpoints are trusted deployment assets. Record their SHA-256 values, restrict who can replace them, and configure the expected hashes before enabling UFD.

The complete AuthVault notice, including the restricted TruFor runtime, is maintained in [`docs/THIRD_PARTY_MODELS.md`](../docs/THIRD_PARTY_MODELS.md).
