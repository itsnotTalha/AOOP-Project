# Third-Party Models and Code

## UniversalFakeDetect

- Detector: UniversalFakeDetect (UFD), using OpenAI CLIP ViT-L/14 image features and the released linear classifier.
- Upstream: [WisconsinAIVision/UniversalFakeDetect](https://github.com/WisconsinAIVision/UniversalFakeDetect).
- Paper: *Towards Universal Fake Image Detectors That Generalize Across Generative Models*, CVPR 2023.
- Repository license: MIT. Deployers must still confirm that separately downloaded checkpoint terms are suitable for their use.
- Checkpoint provenance: manually obtained from the official upstream release instructions; AuthVault does not redistribute or automatically download it.

## OpenAI CLIP

- Dependency: [openai/CLIP](https://github.com/openai/CLIP), pinned to `d05afc436d78f1c48dc0dbf8e5980a9d471f35f6`.
- Repository license: MIT.
- Checkpoint provenance: manually obtained through the official OpenAI CLIP project; AuthVault does not redistribute or automatically download it.

## TruFor

- Detector role: `GENERAL_MANIPULATION_LOCALIZER`, producing a whole-image manipulation score, anomaly map, and reliability map.
- Upstream: [grip-unina/TruFor](https://github.com/grip-unina/TruFor), Image Processing Research Group of University Federico II of Naples (GRIP-UNINA).
- Paper: *TruFor: Leveraging All-Round Clues for Trustworthy Image Forgery Detection and Localization*, CVPR 2023.
- Pinned upstream revision: `ae54475df6f41a491d7615100feb19263dec13f7`.
- License restriction: all rights are reserved. Upstream permits reproduction, modification, and use only for informational and nonprofit purposes and expressly prohibits unauthorized industrial or profit-oriented use. This is not an MIT, Apache, or general commercial-use license.
- Intended AuthVault use: academic/informational/nonprofit evaluation only. Before any commercial use, TruFor must undergo explicit license review, obtain separate permission, or be replaced.
- Checkpoint provenance: the released weights must be manually obtained from the official upstream instructions. The upstream documentation lists MD5 `7bee48f3476c75616c3c5721ab256ff8` for the released weights archive. AuthVault prefers a separately calculated SHA-256 for the extracted `trufor.pth.tar` configured at deployment.
- Source provenance: the minimal official inference runtime is manually provisioned as a Git checkout at the pinned revision. AuthVault verifies checkout `HEAD`; it does not copy the training repository, automatically clone it, or update it during startup or requests.

All model code and checkpoints are administrator-provisioned trusted assets. Keep them outside Git, restrict replacement permissions, record their hashes, and never accept them through public APIs.
