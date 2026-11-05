
import json
import time
import statistics
from concurrent.futures import ThreadPoolExecutor, as_completed
from urllib.request import Request, urlopen

BASES = [
    "http://localhost:8080/api/v1",
    "http://localhost:8081/api/v1"
]

TOTAL_JOBS = 40
SUBMIT_WORKERS = 8
JOB_DURATION_MS = 1000
TIMEOUT_SECONDS = 90


def request_json(url, method="GET", body=None):
    data = json.dumps(body).encode() if body is not None else None

    headers = {}
    if body is not None:
        headers["Content-Type"] = "application/json"

    req = Request(url, data=data, headers=headers, method=method)

    with urlopen(req, timeout=10) as response:
        content = response.read().decode()
        return response.status, json.loads(content) if content else None


def submit_job(index):
    base = BASES[index % len(BASES)]
    started = time.perf_counter()

    status, result = request_json(
        base + "/jobs",
        "POST",
        {
            "type": "WAIT",
            "payload": {"durationMs": JOB_DURATION_MS},
            "maxAttempts": 1
        }
    )

    if status != 201:
        raise RuntimeError(f"Unexpected HTTP status: {status}")

    return {
        "id": result["id"],
        "start": started,
        "submit_ms": (time.perf_counter() - started) * 1000,
        "instance": base
    }


def percentile(values, p):
    values = sorted(values)
    index = max(0, int((len(values) - 1) * p + 0.999))
    return values[index]


def main():
    started = time.perf_counter()

    print(f"Submitting {TOTAL_JOBS} jobs...")

    submitted = []

    with ThreadPoolExecutor(max_workers=SUBMIT_WORKERS) as pool:
        futures = [
            pool.submit(submit_job, i)
            for i in range(TOTAL_JOBS)
        ]

        for future in as_completed(futures):
            submitted.append(future.result())

    jobs = {job["id"]: job for job in submitted}
    ids = set(jobs)

    print(f"Submitted: {len(jobs)}")
    print("Waiting for completion...")

    finished = {}
    latest_status = {}
    peak_running = 0

    while time.perf_counter() - started < TIMEOUT_SECONDS:
        _, running = request_json(
            BASES[0] + "/jobs?status=RUNNING&limit=100"
        )

        active = sum(job["id"] in ids for job in running)
        peak_running = max(peak_running, active)

        _, recent = request_json(
            BASES[0] + "/jobs?limit=100"
        )

        now = time.perf_counter()

        for job in recent:
            job_id = job["id"]

            if job_id not in ids:
                continue

            latest_status[job_id] = job["status"]

            if job["status"] in ("SUCCEEDED", "FAILED", "CANCELLED"):
                finished.setdefault(job_id, now)

        if len(finished) == TOTAL_JOBS:
            break

        time.sleep(0.2)

    elapsed = time.perf_counter() - started

    terminal = len(finished)
    succeeded = sum(
        latest_status.get(job_id) == "SUCCEEDED"
        for job_id in finished
    )

    submit_times = [job["submit_ms"] for job in submitted]

    completion_times = [
        (finished[job_id] - jobs[job_id]["start"]) * 1000
        for job_id in finished
    ]

    print("\n========== BENCHMARK RESULTS ==========")
    print(f"Submitted:             {len(submitted)}")
    print(f"Terminal:              {terminal}")
    print(f"Succeeded:             {succeeded}")
    print(f"Failed:                {terminal - succeeded}")
    print(f"Peak observed RUNNING: {peak_running}")
    print(f"Total elapsed:         {elapsed:.2f} seconds")
    print(f"Throughput:            {terminal / elapsed:.2f} jobs/sec")

    if submit_times:
        print(f"Submit latency p50:    {percentile(submit_times, .50):.2f} ms")
        print(f"Submit latency p95:    {percentile(submit_times, .95):.2f} ms")

    if completion_times:
        print(f"Completion p50:        {percentile(completion_times, .50):.2f} ms")
        print(f"Completion p95:        {percentile(completion_times, .95):.2f} ms")

    print(f"Pending:               {TOTAL_JOBS - terminal}")
    print("=======================================")


if __name__ == "__main__":
    main()
