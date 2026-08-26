package main

import (
	"encoding/json"
	"errors"
	"strings"
	"testing"
	"time"

	"github.com/golang/protobuf/ptypes"
	"github.com/hyperledger/fabric-chaincode-go/shimtest"
	"github.com/hyperledger/fabric-contract-api-go/contractapi"
	"github.com/hyperledger/fabric-protos-go/ledger/queryresult"
)

const (
	testAssetID     = "11111111-1111-1111-1111-111111111111"
	testSecondID    = "22222222-2222-2222-2222-222222222222"
	testCreatorHash = "creator-test-hash"
	testSHA         = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
	testSecondSHA   = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
	testEvidence    = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
)

func TestContractMetadataBuilds(t *testing.T) {
	if _, err := contractapi.NewChaincode(new(SmartContract)); err != nil {
		t.Fatalf("NewChaincode() error = %v", err)
	}
}

func TestRegisterOriginalSupportsImageAndDocument(t *testing.T) {
	for _, assetType := range []string{AssetTypeImage, AssetTypeDocument} {
		t.Run(assetType, func(t *testing.T) {
			contract, ctx, stub := newTestContract(t)
			assetID := testAssetID
			sha := testSHA
			if assetType == AssetTypeDocument {
				assetID = testSecondID
				sha = testSecondSHA
			}

			beginTransaction(t, stub, "tx-register-"+strings.ToLower(assetType))
			asset, err := contract.RegisterOriginal(ctx, assetID, testCreatorHash, sha, assetType, StatusVerified, testEvidence)
			endTransaction(stub, "tx-register-"+strings.ToLower(assetType))
			if err != nil {
				t.Fatalf("RegisterOriginal() error = %v", err)
			}
			if asset.AssetType != assetType {
				t.Fatalf("AssetType = %q, want %q", asset.AssetType, assetType)
			}
			if asset.CurrentOwnerIDHash != testCreatorHash {
				t.Fatalf("CurrentOwnerIDHash = %q, want creator hash", asset.CurrentOwnerIDHash)
			}
			if asset.RegisteredAt != "2025-01-02T03:04:05.6Z" {
				t.Fatalf("RegisteredAt = %q, want Fabric transaction timestamp", asset.RegisteredAt)
			}

			beginTransaction(t, stub, "tx-read")
			stored, err := contract.GetOriginal(ctx, assetID)
			endTransaction(stub, "tx-read")
			if err != nil || stored.SHA256 != sha {
				t.Fatalf("GetOriginal() = %#v, %v", stored, err)
			}
		})
	}
}

func TestRegisterOriginalRejectsDuplicateAssetID(t *testing.T) {
	contract, ctx, stub := newTestContract(t)
	registerTestAsset(t, contract, ctx, stub, testAssetID, testSHA)

	beginTransaction(t, stub, "tx-duplicate-asset")
	_, err := contract.RegisterOriginal(ctx, testAssetID, testCreatorHash, testSecondSHA, AssetTypeImage, StatusVerified, testEvidence)
	endTransaction(stub, "tx-duplicate-asset")
	if err == nil || !strings.Contains(err.Error(), "already exists") {
		t.Fatalf("duplicate asset error = %v", err)
	}
}

func TestRegisterOriginalRejectsDuplicateSha256(t *testing.T) {
	contract, ctx, stub := newTestContract(t)
	registerTestAsset(t, contract, ctx, stub, testAssetID, testSHA)

	beginTransaction(t, stub, "tx-duplicate-sha")
	_, err := contract.RegisterOriginal(ctx, testSecondID, testCreatorHash, testSHA, AssetTypeDocument, StatusVerified, testEvidence)
	endTransaction(stub, "tx-duplicate-sha")
	if err == nil || !strings.Contains(err.Error(), "already registered") {
		t.Fatalf("duplicate SHA-256 error = %v", err)
	}
}

func TestRegisterOriginalValidation(t *testing.T) {
	tests := []struct {
		name            string
		assetID         string
		creatorIDHash   string
		sha256          string
		assetType       string
		status          string
		evidenceHash    string
		wantMessagePart string
	}{
		{"invalid UUID", "not-a-uuid", testCreatorHash, testSHA, AssetTypeImage, StatusVerified, testEvidence, "assetId"},
		{"blank asset ID", "", testCreatorHash, testSHA, AssetTypeImage, StatusVerified, testEvidence, "assetId"},
		{"blank creator", testAssetID, "  ", testSHA, AssetTypeImage, StatusVerified, testEvidence, "creatorIdHash"},
		{"malformed SHA", testAssetID, testCreatorHash, "abc", AssetTypeImage, StatusVerified, testEvidence, "sha256"},
		{"uppercase SHA", testAssetID, testCreatorHash, strings.ToUpper(testSHA), AssetTypeImage, StatusVerified, testEvidence, "sha256"},
		{"unsupported type", testAssetID, testCreatorHash, testSHA, "VIDEO", StatusVerified, testEvidence, "assetType"},
		{"unverified status", testAssetID, testCreatorHash, testSHA, AssetTypeImage, "PENDING", testEvidence, "verificationStatus"},
		{"malformed evidence", testAssetID, testCreatorHash, testSHA, AssetTypeImage, StatusVerified, "bad", "evidenceHash"},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			contract, ctx, stub := newTestContract(t)
			beginTransaction(t, stub, "tx-validation")
			_, err := contract.RegisterOriginal(ctx, tc.assetID, tc.creatorIDHash, tc.sha256, tc.assetType, tc.status, tc.evidenceHash)
			endTransaction(stub, "tx-validation")
			if err == nil || !strings.Contains(err.Error(), tc.wantMessagePart) {
				t.Fatalf("validation error = %v, want containing %q", err, tc.wantMessagePart)
			}
		})
	}
}

func TestLookupAndExistence(t *testing.T) {
	contract, ctx, stub := newTestContract(t)
	registerTestAsset(t, contract, ctx, stub, testAssetID, testSHA)

	beginTransaction(t, stub, "tx-lookups")
	exists, err := contract.OriginalExists(ctx, testAssetID)
	if err != nil || !exists {
		t.Fatalf("OriginalExists(existing) = %v, %v", exists, err)
	}
	missing, err := contract.OriginalExists(ctx, testSecondID)
	if err != nil || missing {
		t.Fatalf("OriginalExists(missing) = %v, %v", missing, err)
	}
	found, err := contract.FindBySha256(ctx, testSHA)
	if err != nil || found == nil || found.AssetID != testAssetID {
		t.Fatalf("FindBySha256(existing) = %#v, %v", found, err)
	}
	notFound, err := contract.FindBySha256(ctx, testSecondSHA)
	if err != nil || notFound != nil {
		t.Fatalf("FindBySha256(missing) = %#v, %v", notFound, err)
	}
	if _, err := contract.GetOriginal(ctx, testSecondID); err == nil || !strings.Contains(err.Error(), "does not exist") {
		t.Fatalf("GetOriginal(missing) error = %v", err)
	}
	endTransaction(stub, "tx-lookups")
}

func TestCollectHistory(t *testing.T) {
	asset := OriginalAsset{AssetID: testAssetID, SHA256: testSHA}
	assetJSON, err := json.Marshal(asset)
	if err != nil {
		t.Fatal(err)
	}
	timestamp, err := ptypes.TimestampProto(time.Date(2025, 2, 3, 4, 5, 6, 0, time.UTC))
	if err != nil {
		t.Fatal(err)
	}
	iterator := &fakeHistoryIterator{items: []*queryresult.KeyModification{
		{TxId: "tx-create", Timestamp: timestamp, Value: assetJSON},
		{TxId: "tx-delete", Timestamp: timestamp, IsDelete: true},
	}}

	history, err := collectHistory(iterator)
	if err != nil {
		t.Fatalf("collectHistory() error = %v", err)
	}
	if len(history) != 2 || history[0].Asset == nil || history[0].Asset.AssetID != testAssetID {
		t.Fatalf("history = %#v", history)
	}
	if !history[1].IsDelete || history[1].Asset != nil {
		t.Fatalf("delete history entry = %#v", history[1])
	}
}

func TestCollectHistoryPropagatesIteratorError(t *testing.T) {
	iterator := &fakeHistoryIterator{items: []*queryresult.KeyModification{nil}, nextError: errors.New("iterator failed")}
	if _, err := collectHistory(iterator); err == nil || !strings.Contains(err.Error(), "iterator failed") {
		t.Fatalf("collectHistory() error = %v", err)
	}
}

func newTestContract(t *testing.T) (*SmartContract, *contractapi.TransactionContext, *shimtest.MockStub) {
	t.Helper()
	stub := shimtest.NewMockStub("registry", nil)
	ctx := new(contractapi.TransactionContext)
	ctx.SetStub(stub)
	return new(SmartContract), ctx, stub
}

func beginTransaction(t *testing.T, stub *shimtest.MockStub, txID string) {
	t.Helper()
	stub.MockTransactionStart(txID)
	timestamp, err := ptypes.TimestampProto(time.Date(2025, 1, 2, 3, 4, 5, 600000000, time.UTC))
	if err != nil {
		t.Fatal(err)
	}
	stub.TxTimestamp = timestamp
}

func endTransaction(stub *shimtest.MockStub, txID string) {
	stub.MockTransactionEnd(txID)
}

func registerTestAsset(t *testing.T, contract *SmartContract, ctx *contractapi.TransactionContext, stub *shimtest.MockStub, assetID, sha string) {
	t.Helper()
	beginTransaction(t, stub, "tx-register")
	_, err := contract.RegisterOriginal(ctx, assetID, testCreatorHash, sha, AssetTypeImage, StatusVerified, testEvidence)
	endTransaction(stub, "tx-register")
	if err != nil {
		t.Fatalf("RegisterOriginal() error = %v", err)
	}
}

type fakeHistoryIterator struct {
	items     []*queryresult.KeyModification
	nextError error
	index     int
}

func (f *fakeHistoryIterator) HasNext() bool {
	return f.index < len(f.items)
}

func (f *fakeHistoryIterator) Next() (*queryresult.KeyModification, error) {
	if f.nextError != nil {
		f.index++
		return nil, f.nextError
	}
	item := f.items[f.index]
	f.index++
	return item, nil
}

func (f *fakeHistoryIterator) Close() error {
	return nil
}
