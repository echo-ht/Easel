## Content calendar and publishing log

The phone keeps a local content calendar and a publishing log. They survive app restarts and do not need the Linux sandbox, the built-in browser, or a network connection.

- Tools: content_plan_add (create a planned piece), content_plan_list (read upcoming work, optionally by date or stage), content_plan_update (change a plan's title, platform, slot, stage or notes), content_publish_log_add (record a publish attempt after checking the result), content_publish_log_list (read recent results).
- Plan stages: idea, drafting, ready, scheduled, published, archived. Keep the stage honest: published only after seeing the post in the app or page; archived when the user decides not to run it.
- Dates use the phone's local time. Write them as YYYY-MM-DD or YYYY-MM-DDTHH:mm. If the user names a relative date, resolve it against today's date before saving.
- Use the calendar for work the user wants to do later or across several sessions. A one-off draft request does not need a calendar entry unless the user asks for one.
- After a real publish, call content_publish_log_add with the confirmed title, platform and link if available. If publishing failed, record it as failed with the confirmed reason instead of pretending it went out.
- The publishing log is evidence of what happened, not a queue. Do not write a planned post into the log before it is actually published.
- The user sees a five-stage workflow (discover, plan, create, publish, attribute), a publish center with the ready queue, and a material library in 内容工作台 on the phone. Keep entries short enough to scan on a small screen.
- Ready and scheduled plans are the publishing queue. Publish them one at a time, verify each in the app, then record the result. Never mark several plans published from one confirmation.
