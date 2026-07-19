"""POST /normalize-recipients -- canonicalizes a user's recipient names via
training/merchant_normalizer.py's two-tier (UPI-ID grouping + fuzzy
clustering) pipeline, plus (2026-07-19) surfaces ambiguous anchor/candidate
groups the same clustering pass considered but did not confidently auto-merge,
for the Merge Payees human-review feature.

Internal-access-only, same as every other route in this service
(api/security.py's InternalKeyMiddleware). Per CLAUDE.md's "Categorization is
the ML gateway" decision, no module but Spring Boot's Categorization calls
this FastAPI service at all -- here that's the RecipientCanonicalizationJob,
via CategorizationService#normalizeRecipients.

Unlike /predict, this isn't a per-transaction call: the clustering algorithm
compares every entry in one request against every other, so the caller must
send one user's full recipient set per call, never multiple users mixed
together (see NormalizeRecipientsRequest's docstring).

ambiguous_groups is returned from the SAME clustering pass that produces
canonical_names, not a second endpoint/call -- re-deriving the clusters twice
per user would double this route's cost and risk the two answers disagreeing
if anything about the entries changed between calls.
"""

from fastapi import APIRouter

from api.schemas import NormalizeRecipientsRequest, NormalizeRecipientsResponse
from training.merchant_normalizer import canonicalize_with_ambiguities

router = APIRouter()


@router.post("/normalize-recipients", response_model=NormalizeRecipientsResponse)
def normalize_recipients(request: NormalizeRecipientsRequest) -> NormalizeRecipientsResponse:
    entries = [entry.model_dump() for entry in request.entries]
    result = canonicalize_with_ambiguities(entries)
    return NormalizeRecipientsResponse(**result)
