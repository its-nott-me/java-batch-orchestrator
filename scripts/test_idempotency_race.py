
import json
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from urllib.request import Request, urlopen
from urllib.error import HTTPError

BASES = [
    "http://localhost:8080/api/v1/jobs",
    "http://localhost:8081/api/v1/jobs"
]

REQUEST_COUNT = 20
KEY = f"race-test-{uuid.uuid4()}"

PAYLOAD = {
    "type": "ECHO",
    "payload": {
        "message": "Concurrent idempotency test"
    },
    "maxAttempts": 1
}


def submit(index):
    url = BASES[index % 2]

    req = Request(
        url,
        data=json.dumps(PAYLOAD).encode(),
        headers={
            "Content-Type": "application/json",
            "Idempotency-Key": KEY
        },
        method="POST"
    )

    try:
        with urlopen(req, timeout=15) as response:
            body = json.loads(response.read().decode())
            return response.status, body.get("id"), None

    except HTTPError as error:
        body = error.read().decode()
        return error.code, None, body

    except Exception as error:
        return None, None, str(error)


def main():
    results = []

    print(f"Submitting {REQUEST_COUNT} simultaneous requests")
    print(f"Idempotency key: {KEY}")

    with ThreadPoolExecutor(max_workers=REQUEST_COUNT) as pool:
        futures = [
            pool.submit(submit, i)
            for i in range(REQUEST_COUNT)
        ]

        for future in as_completed(futures):
            results.append(future.result())

    ids = {job_id for _, job_id, _ in results if job_id}
    statuses = {}

    for status, _, _ in results:
        statuses[status] = statuses.get(status, 0) + 1

    errors = [
        error for _, _, error in results if error
    ]

    print("\n========== RESULTS ==========")
    print("Total requests:", len(results))
    print("HTTP statuses:", statuses)
    print("Unique job IDs:", len(ids))
    print("Job IDs:", ids)
    print("Errors:", errors)
    print("=============================")

    passed = (
        len(results) == REQUEST_COUNT
        and len(ids) == 1
        and not errors
        and all(status in (200, 201) for status, _, _ in results)
    )

    print("RESULT:", "PASS" if passed else "FAIL")


if __name__ == "__main__":
    main()
